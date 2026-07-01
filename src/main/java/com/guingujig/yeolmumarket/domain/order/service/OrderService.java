package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MySaleListItemResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final YeolmuProperties yeolmuProperties;

  /**
   * 이미 조회·검증·예약된 구매자와 상품으로 CREATED 주문을 저장한다.
   *
   * <p>상품 공개 여부, 구매 가능 상태, 판매자 본인 주문 검증과 RESERVED 전이는 호출자가 완료해야 한다.
   */
  @Transactional
  public CreateOrderResponse createOrder(User buyer, Product product) {
    Order order = Order.create(buyer, product);
    Order savedOrder = orderRepository.save(order);
    return CreateOrderResponse.from(savedOrder);
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
    Order order = getExistingOrderWithDetails(orderId);

    order.validateParticipant(requesterId);

    return GetOrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public Order getExistingOrderWithDetails(Long orderId) {
    return orderRepository
        .findWithDetailsById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > yeolmuProperties.pagination().maxPageSize()) {
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
}
