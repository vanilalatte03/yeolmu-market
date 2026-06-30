package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.ConfirmOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MySaleListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.RegisterOrderShippingResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final int MAX_PAGE_SIZE = 100;

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 로그인한 구매자가 판매 중인 상품을 주문한다.
   *
   * <p>주문 생성과 상품 상태 변경(ON_SALE → RESERVED)을 하나의 트랜잭션에서 처리한다. flush를 명시적으로 호출해 Product.@Version 낙관적
   * 락 충돌을 서비스 내에서 포착하고 ORDER_ALREADY_EXISTS로 변환한다.
   *
   * @throws BusinessException PRODUCT_NOT_FOUND - 상품이 존재하지 않거나 삭제/숨김 처리된 경우
   * @throws BusinessException CANNOT_ORDER_OWN_PRODUCT - 자신의 상품을 주문하려는 경우
   * @throws BusinessException PRODUCT_NOT_ON_SALE - 상품이 ON_SALE 상태가 아닌 경우
   * @throws BusinessException ORDER_ALREADY_EXISTS - 낙관적 락 충돌로 동시 주문이 실패한 경우
   */
  @Transactional
  public CreateOrderResponse createOrder(Long buyerId, Long productId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Product product =
        productRepository
            .findWithSellerById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    product.reserveForOrder(buyerId);

    try {
      productRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.ORDER_ALREADY_EXISTS);
    }

    Order order = Order.create(buyer, product);
    orderRepository.save(order);

    publishProductSearchCacheEviction();
    return CreateOrderResponse.from(order);
  }

  /**
   * 주문 구매자가 CREATED 상태의 주문을 취소하고 예약된 상품을 ON_SALE로 되돌린다.
   *
   * <p>주문 상태 변경과 상품 상태 변경을 하나의 트랜잭션에서 처리한다. flush를 명시적으로 호출해 Product.@Version 낙관적 락 충돌을 서비스 내에서
   * 포착하고 INVALID_ORDER_STATUS로 변환한다. canceledAt은 flush와 refresh 후 DB가 확정한 modifiedAt 값으로 반환한다.
   *
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 구매자가 아닌 사용자가 취소하는 경우
   * @throws BusinessException INVALID_ORDER_STATUS - CREATED가 아닌 주문을 취소하는 경우, 또는 동시 취소 경합 시
   */
  @Transactional
  public CancelOrderResponse cancelOrder(Long requesterId, Long orderId) {
    orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateBuyer(requesterId);

    order.cancelAndReleaseProduct();

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    publishProductSearchCacheEviction();
    return CancelOrderResponse.from(order);
  }

  /**
   * 로그인한 판매자가 PAID 상태 주문에 배송 증빙을 등록한다.
   *
   * <p>배송 증빙 등록은 주문 상태만 SHIPPING으로 전이하며 상품은 RESERVED, 결제는 PAID 상태를 유지한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 송장 번호가 누락, blank, 또는 trim 후 100자를 초과하는 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 판매자가 아닌 사용자의 요청
   * @throws BusinessException INVALID_ORDER_STATUS - PAID가 아닌 주문에 배송 증빙 등록 요청
   */
  @Transactional
  public RegisterOrderShippingResponse registerShipping(
      Long sellerId, Long orderId, String trackingNumber) {
    String normalizedTrackingNumber = normalizeTrackingNumber(trackingNumber);

    orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateSeller(sellerId);

    order.registerShipping(normalizedTrackingNumber, LocalDateTime.now(ZoneOffset.UTC));

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    return RegisterOrderShippingResponse.from(order);
  }

  /**
   * 로그인한 구매자가 SHIPPING 상태 주문을 구매확정하고 상품을 SOLD_OUT으로 전이한다.
   *
   * <p>주문과 상품 상태 변경을 하나의 트랜잭션에서 처리하며, 상품 상태 변경 후 검색 캐시 무효화 이벤트를 발행한다. confirmedAt은 DB가 확정한 주문
   * modifiedAt 값을 사용한다.
   *
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 구매자가 아닌 사용자의 요청
   * @throws BusinessException INVALID_ORDER_STATUS - SHIPPING이 아닌 주문 또는 판매 완료 처리할 수 없는 상품 상태
   */
  @Transactional
  public ConfirmOrderResponse confirmOrder(Long buyerId, Long orderId) {
    orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateBuyer(buyerId);

    order.confirmPurchaseAndCompleteProduct();

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    publishProductSearchCacheEviction();
    return ConfirmOrderResponse.from(order);
  }

  /**
   * 로그인 사용자가 구매자로 참여한 주문 목록을 페이지 단위로 조회한다.
   *
   * <p>status가 null이면 모든 주문 상태를 조회하고, 값이 있으면 해당 상태만 필터링한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<MyOrderListItemResponse> getMyOrders(
      Long buyerId, int page, int size, OrderStatus status) {
    validatePagination(page, size);
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    Page<Order> orders = findBuyerOrders(buyerId, status, pageable);
    return PageResponse.from(orders.map(MyOrderListItemResponse::from));
  }

  /**
   * 로그인 사용자가 판매자로 참여한 주문 목록을 페이지 단위로 조회한다.
   *
   * <p>status가 null이면 모든 주문 상태를 조회하고, 값이 있으면 해당 상태만 필터링한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<MySaleListItemResponse> getMySales(
      Long sellerId, int page, int size, OrderStatus status) {
    validatePagination(page, size);
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    Page<Order> orders = findSellerOrders(sellerId, status, pageable);
    return PageResponse.from(orders.map(MySaleListItemResponse::from));
  }

  /**
   * 주문 구매자 또는 판매자가 주문 상세 정보를 조회한다.
   *
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 구매자·판매자가 아닌 사용자가 조회하는 경우
   */
  @Transactional(readOnly = true)
  public GetOrderResponse getOrder(Long requesterId, Long orderId) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateParticipant(requesterId);

    return GetOrderResponse.from(order);
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private Page<Order> findBuyerOrders(Long buyerId, OrderStatus status, PageRequest pageable) {
    if (status == null) {
      return orderRepository.findByBuyerId(buyerId, pageable);
    }
    return orderRepository.findByBuyerIdAndOrderStatus(buyerId, status, pageable);
  }

  private Page<Order> findSellerOrders(Long sellerId, OrderStatus status, PageRequest pageable) {
    if (status == null) {
      return orderRepository.findBySellerId(sellerId, pageable);
    }
    return orderRepository.findBySellerIdAndOrderStatus(sellerId, status, pageable);
  }

  private String normalizeTrackingNumber(String trackingNumber) {
    if (trackingNumber == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedTrackingNumber = trackingNumber.trim();
    if (normalizedTrackingNumber.isBlank() || normalizedTrackingNumber.length() > 100) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedTrackingNumber;
  }

  private void publishProductSearchCacheEviction() {
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
  }
}
