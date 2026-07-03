package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.ConfirmOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MySaleListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.RegisterOrderShippingResponse;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.DistributedLockExecutor;
import com.guingujig.yeolmumarket.global.lock.LockKeys;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

  private final UserService userService;
  private final ProductService productService;
  private final OrderService orderService;
  private final DistributedLockExecutor distributedLockExecutor;
  private final OrderLockedCommandService orderLockedCommandService;

  /**
   * 로그인한 구매자가 판매 중인 상품을 주문한다.
   *
   * <p>구매자 조회와 상품 예약은 각 도메인 Service 계약으로 위임하고, OrderService는 준비된 엔티티로 주문 저장만 담당한다.
   */
  @Transactional
  public CreateOrderResponse createOrder(Long buyerId, Long productId) {
    User buyer = userService.getExistingUser(buyerId);
    Product product = productService.reservePublicProductForOrder(buyerId, productId);
    return orderService.createOrder(buyer, product);
  }

  /**
   * 주문 구매자가 CREATED 상태의 주문을 취소하고 예약된 상품을 ON_SALE로 되돌린다.
   *
   * <p>주문 단위 분산락을 획득한 뒤 실제 상태 전이는 OrderLockedCommandService에 위임한다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CancelOrderResponse cancelOrder(Long requesterId, Long orderId) {
    return distributedLockExecutor.execute(
        LockKeys.order(orderId), () -> orderLockedCommandService.cancelOrder(requesterId, orderId));
  }

  /**
   * 로그인한 판매자가 PAID 상태 주문에 배송 증빙을 등록한다.
   *
   * <p>송장 번호 정규화와 주문 단위 분산락 획득을 담당하고, 실제 주문 상태 전이는 잠금 경계 안의 command 서비스에 위임한다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public RegisterOrderShippingResponse registerShipping(
      Long sellerId, Long orderId, String trackingNumber) {
    String normalizedTrackingNumber = normalizeTrackingNumber(trackingNumber);

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () ->
            orderLockedCommandService.registerShipping(
                sellerId, orderId, normalizedTrackingNumber));
  }

  /**
   * 로그인한 구매자가 SHIPPING 상태 주문을 구매확정하고 상품을 SOLD_OUT으로 전이한다.
   *
   * <p>주문 단위 분산락을 획득한 뒤 실제 상태 전이는 OrderLockedCommandService에 위임한다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ConfirmOrderResponse confirmOrder(Long buyerId, Long orderId) {
    return distributedLockExecutor.execute(
        LockKeys.order(orderId), () -> orderLockedCommandService.confirmOrder(buyerId, orderId));
  }

  @Transactional(readOnly = true)
  public PageResponse<MyOrderListItemResponse> getMyOrders(
      Long buyerId, int page, int size, OrderStatus status) {
    return orderService.getMyOrders(buyerId, page, size, status);
  }

  @Transactional(readOnly = true)
  public PageResponse<MySaleListItemResponse> getMySales(
      Long sellerId, int page, int size, OrderStatus status) {
    return orderService.getMySales(sellerId, page, size, status);
  }

  @Transactional(readOnly = true)
  public GetOrderResponse getOrder(Long requesterId, Long orderId) {
    return orderService.getOrder(requesterId, orderId);
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
}
