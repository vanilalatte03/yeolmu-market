package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.ConfirmOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.RegisterOrderShippingResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderLockedCommandService {

  private final OrderRepository orderRepository;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
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

    publishProductSearchCacheEviction();
    return CancelOrderResponse.from(order);
  }

  @Transactional
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

  @Transactional
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

    publishProductSearchCacheEviction();
    return ConfirmOrderResponse.from(order);
  }

  private Order findOrder(Long orderId) {
    return orderRepository
        .findWithDetailsById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }

  private void publishProductSearchCacheEviction() {
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
  }
}
