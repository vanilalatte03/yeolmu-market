package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.ConfirmOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.RegisterOrderShippingResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductChangeEventPublisher;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderLockedCommandService {

  private final OrderRepository orderRepository;
  private final EntityManager entityManager;
  private final ProductChangeEventPublisher productChangeEventPublisher;

  @LockBoundedTransactional
  public CancelOrderResponse cancelOrder(Long requesterId, Long orderId) {
    Order order = findOrder(orderId);

    order.validateBuyer(requesterId);

    order.cancelAndReleaseProduct();

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    publishProductStatusChanged(
        order.getProduct().getId(), ProductStatus.RESERVED, ProductStatus.ON_SALE);
    return CancelOrderResponse.from(order);
  }

  @LockBoundedTransactional
  public RegisterOrderShippingResponse registerShipping(
      Long sellerId, Long orderId, String trackingNumber) {
    Order order = findOrder(orderId);

    order.validateSeller(sellerId);

    order.registerShipping(trackingNumber, LocalDateTime.now(ZoneOffset.UTC));

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    return RegisterOrderShippingResponse.from(order);
  }

  @LockBoundedTransactional
  public ConfirmOrderResponse confirmOrder(Long buyerId, Long orderId) {
    Order order = findOrder(orderId);

    order.validateBuyer(buyerId);

    order.confirmPurchaseAndCompleteProduct();

    try {
      orderRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    entityManager.refresh(order);

    publishProductStatusChanged(
        order.getProduct().getId(), ProductStatus.RESERVED, ProductStatus.SOLD_OUT);
    return ConfirmOrderResponse.from(order);
  }

  private Order findOrder(Long orderId) {
    return orderRepository
        .findWithDetailsById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }

  private void publishProductStatusChanged(Long productId, ProductStatus... affectedStatuses) {
    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(productId, affectedStatuses);
  }
}
