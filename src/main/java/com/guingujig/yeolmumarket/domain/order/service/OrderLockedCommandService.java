package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.ConfirmOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.RegisterOrderShippingResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
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

    validateBuyer(order, requesterId);

    order.cancel();
    order.getProduct().cancelReservation();

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

    validateSeller(order, sellerId);

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

    validateBuyer(order, buyerId);

    order.confirmPurchase();
    completeProductSale(order.getProduct());

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

  private void completeProductSale(Product product) {
    if (product.getStatus() != ProductStatus.RESERVED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    product.completeSale();
  }

  private void publishProductSearchCacheEviction() {
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
  }

  private void validateBuyer(Order order, Long userId) {
    if (!isBuyer(order, userId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }
  }

  private void validateSeller(Order order, Long userId) {
    if (!isSeller(order, userId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }
  }

  private boolean isBuyer(Order order, Long userId) {
    return Objects.equals(order.getBuyer().getId(), userId);
  }

  private boolean isSeller(Order order, Long userId) {
    return Objects.equals(order.getSeller().getId(), userId);
  }
}
