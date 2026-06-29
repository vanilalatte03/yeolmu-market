package com.guingujig.yeolmumarket.domain.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundServiceDataIntegrityTest {

  @Mock private OrderRepository orderRepository;
  @Mock private RefundRequestRepository refundRequestRepository;

  private RefundService refundService;

  @BeforeEach
  void setUp() {
    refundService = new RefundService(orderRepository, refundRequestRepository);
  }

  @Test
  void 환불_요청_저장_중_주문_unique_제약_위반이면_중복_요청으로_변환한다() {
    Order order = shippingOrder();
    when(orderRepository.findWithDetailsByIdForUpdate(1L)).thenReturn(Optional.of(order));
    when(refundRequestRepository.existsByOrder_Id(1L)).thenReturn(false);
    when(refundRequestRepository.saveAndFlush(any(RefundRequest.class)))
        .thenThrow(duplicateRefundRequestException());

    assertThatThrownBy(() -> refundService.createRefundRequest(1L, 1L, "환불 요청"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS));
  }

  @Test
  void 환불_요청_저장_중_다른_무결성_예외이면_그대로_던진다() {
    Order order = shippingOrder();
    DataIntegrityViolationException dataIntegrityViolationException =
        new DataIntegrityViolationException("not-null violation");
    when(orderRepository.findWithDetailsByIdForUpdate(1L)).thenReturn(Optional.of(order));
    when(refundRequestRepository.existsByOrder_Id(1L)).thenReturn(false);
    when(refundRequestRepository.saveAndFlush(any(RefundRequest.class)))
        .thenThrow(dataIntegrityViolationException);

    assertThatThrownBy(() -> refundService.createRefundRequest(1L, 1L, "환불 요청"))
        .isSameAs(dataIntegrityViolationException);
  }

  private DataIntegrityViolationException duplicateRefundRequestException() {
    ConstraintViolationException constraintViolationException =
        new ConstraintViolationException(
            "duplicate refund request", new SQLException(), "uk_refund_request_order");
    return new DataIntegrityViolationException(
        "duplicate refund request", constraintViolationException);
  }

  private Order shippingOrder() {
    User seller = new User("seller@example.com", "encoded-password", "열무판매자");
    User buyer = new User("buyer@example.com", "encoded-password", "열무구매자");
    ReflectionTestUtils.setField(buyer, "id", 1L);
    Category category = Category.create("디지털기기");
    Product product = Product.create(seller, "아이패드 미니", "생활기스 조금 있습니다.", 430000, category);
    product.reserve();
    Order order = Order.create(buyer, product);
    order.markAsPaid();
    order.registerShipping("1234-5678-9012", LocalDateTime.of(2026, 6, 24, 10, 0));
    return order;
  }
}
