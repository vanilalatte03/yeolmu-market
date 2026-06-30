package com.guingujig.yeolmumarket.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 6, 30, 10, 0);

  @Test
  void 같은_주문의_결제이면_주문_검증을_통과한다() {
    Order order = createOrder("buyer@example.com", "seller@example.com");
    Payment payment =
        Payment.createPaid(order, PaymentMethod.MOCK_CARD, "idempotency-key", PAID_AT);

    assertThatNoException().isThrownBy(() -> payment.validateOrder(order));
  }

  @Test
  void 다른_주문의_결제이면_PAYMENT_NOT_FOUND가_발생한다() {
    Order paymentOrder = createOrder("buyer1@example.com", "seller1@example.com");
    Order requestedOrder = createOrder("buyer2@example.com", "seller2@example.com");
    Payment payment =
        Payment.createPaid(paymentOrder, PaymentMethod.MOCK_CARD, "idempotency-key", PAID_AT);

    assertThatThrownBy(() -> payment.validateOrder(requestedOrder))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
  }

  private Order createOrder(String buyerEmail, String sellerEmail) {
    User buyer = new User(buyerEmail, "password", "buyer");
    User seller = new User(sellerEmail, "password", "seller");
    Category category = Category.create("디지털기기");
    Product product = Product.create(seller, "상품명", "상품 설명", 1000, category);
    return Order.create(buyer, product);
  }
}
