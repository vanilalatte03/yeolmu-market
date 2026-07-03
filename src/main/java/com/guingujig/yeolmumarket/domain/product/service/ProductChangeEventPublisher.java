package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.service.ProductDisplayChangedEvent;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchIndexChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductChangeEventPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public void publishSearchIndexAndDisplayChanged(
      Long productId, ProductStatus... affectedStatuses) {
    publishSearchIndexChanged(productId, affectedStatuses);
    publishDisplayChanged(productId);
  }

  public void publishSearchIndexChanged(Long productId, ProductStatus... affectedStatuses) {
    eventPublisher.publishEvent(new ProductSearchIndexChangedEvent(productId, affectedStatuses));
  }

  public void publishDisplayChanged(Long productId) {
    eventPublisher.publishEvent(new ProductDisplayChangedEvent(productId));
  }
}
