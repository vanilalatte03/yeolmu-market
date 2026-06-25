package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final EntityManager entityManager;

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

    if (product.isDeleted() || product.isHidden()) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }

    if (Objects.equals(product.getSeller().getId(), buyerId)) {
      throw new BusinessException(ErrorCode.CANNOT_ORDER_OWN_PRODUCT);
    }

    if (product.getStatus() != ProductStatus.ON_SALE) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    product.reserve();

    try {
      productRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.ORDER_ALREADY_EXISTS);
    }

    Order order = Order.create(buyer, product);
    orderRepository.save(order);

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
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    if (!Objects.equals(order.getBuyer().getId(), requesterId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    order.cancel();
    order.getProduct().cancelReservation();

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    return CancelOrderResponse.from(order);
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

    boolean isBuyer = Objects.equals(order.getBuyer().getId(), requesterId);
    boolean isSeller = Objects.equals(order.getSeller().getId(), requesterId);
    if (!isBuyer && !isSeller) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    return GetOrderResponse.from(order);
  }
}
