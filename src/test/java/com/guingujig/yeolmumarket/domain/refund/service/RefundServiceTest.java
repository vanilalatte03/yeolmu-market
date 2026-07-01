package com.guingujig.yeolmumarket.domain.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

  @Mock private RefundRequestRepository refundRequestRepository;
  @Mock private RefundRequest refundRequest;

  private RefundService refundService;

  @BeforeEach
  void setUp() {
    refundService = new RefundService(refundRequestRepository);
  }

  @Test
  void 환불_ID로_주문_ID를_조회한다() {
    when(refundRequestRepository.findOrderIdById(10L)).thenReturn(Optional.of(20L));

    Long orderId = refundService.findOrderIdByRefundId(10L);

    assertThat(orderId).isEqualTo(20L);
  }

  @Test
  void 환불_ID로_주문_ID를_조회할_수_없으면_예외를_던진다() {
    when(refundRequestRepository.findOrderIdById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> refundService.findOrderIdByRefundId(10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Test
  void 처리용_환불_요청을_주문_판매자_상품과_함께_조회한다() {
    when(refundRequestRepository.findWithOrderSellerAndProductById(10L))
        .thenReturn(Optional.of(refundRequest));

    RefundRequest found = refundService.getRefundRequestWithOrderSellerAndProduct(10L);

    assertThat(found).isSameAs(refundRequest);
  }

  @Test
  void 처리용_환불_요청이_없으면_예외를_던진다() {
    when(refundRequestRepository.findWithOrderSellerAndProductById(10L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> refundService.getRefundRequestWithOrderSellerAndProduct(10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Test
  void 주문에_환불_요청이_이미_있으면_중복_예외를_던진다() {
    when(refundRequestRepository.existsByOrder_Id(20L)).thenReturn(true);

    assertThatThrownBy(() -> refundService.validateNoExistingRefundRequest(20L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS));
  }

  @Test
  void 새_환불_요청을_저장하고_flush한다() {
    when(refundRequestRepository.saveAndFlush(refundRequest)).thenReturn(refundRequest);

    RefundRequest saved = refundService.saveNewRefundRequestAndFlush(refundRequest);

    assertThat(saved).isSameAs(refundRequest);
  }

  @Test
  void 새_환불_요청_저장_중_중복_제약이_발생하면_중복_예외로_변환한다() {
    when(refundRequestRepository.saveAndFlush(refundRequest))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key", hibernateConstraintViolation("public.uk_refund_request_order")));

    assertThatThrownBy(() -> refundService.saveNewRefundRequestAndFlush(refundRequest))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS));
  }

  @Test
  void 새_환불_요청_저장_중_낙관락_실패가_발생하면_주문_상태_예외로_변환한다() {
    when(refundRequestRepository.saveAndFlush(refundRequest))
        .thenThrow(new ObjectOptimisticLockingFailureException(RefundRequest.class, 10L));

    assertThatThrownBy(() -> refundService.saveNewRefundRequestAndFlush(refundRequest))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
  }

  @Test
  void 환불_상태_변경을_flush한다() {
    refundService.flushRefundRequestStatusChange();

    verify(refundRequestRepository).flush();
  }

  @Test
  void 환불_상태_변경_flush_중_낙관락_실패가_발생하면_환불_상태_예외로_변환한다() {
    doThrow(new ObjectOptimisticLockingFailureException(RefundRequest.class, 10L))
        .when(refundRequestRepository)
        .flush();

    assertThatThrownBy(() -> refundService.flushRefundRequestStatusChange())
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));
  }

  private static org.hibernate.exception.ConstraintViolationException hibernateConstraintViolation(
      String constraint) {
    return new org.hibernate.exception.ConstraintViolationException(
        "unique constraint violation", new SQLException(), constraint);
  }
}
