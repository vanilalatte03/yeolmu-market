package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CreateOrderResponse(
    Long orderId,
    ProductInfo product,
    UserInfo buyer,
    UserInfo seller,
    OrderStatus status,
    OffsetDateTime createdAt) {

  public static CreateOrderResponse from(Order order) {
    return new CreateOrderResponse(
        order.getId(),
        ProductInfo.from(order.getProduct(), order.getOrderPrice()),
        UserInfo.from(order.getBuyer()),
        UserInfo.from(order.getSeller()),
        order.getOrderStatus(),
        order.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  public record ProductInfo(Long productId, String title, Integer price) {
    public static ProductInfo from(Product product, Integer orderPrice) {
      return new ProductInfo(product.getId(), product.getTitle(), orderPrice);
    }
  }

  public record UserInfo(Long userId, String nickname) {
    public static UserInfo from(User user) {
      return new UserInfo(user.getId(), user.getNickname());
    }
  }
}
