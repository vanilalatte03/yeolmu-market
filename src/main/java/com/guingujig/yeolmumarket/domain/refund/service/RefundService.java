package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.DistributedLockExecutor;
import com.guingujig.yeolmumarket.global.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

  private final RefundRequestRepository refundRequestRepository;
  private final DistributedLockExecutor distributedLockExecutor;
  private final RefundLockedCommandService refundLockedCommandService;

  /**
   * 로그인한 구매자가 SHIPPING 상태 주문에 환불 요청을 생성하고 주문을 REFUND_REQUESTED로 전이한다.
   *
   * <p>공통 주문 분산락으로 동일 주문 환불 요청 생성을 직렬화하고, 성공 시 상품은 RESERVED, 결제는 PAID 상태를 유지한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 사유가 누락, blank, 또는 trim 후 255자를 초과하는 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 구매자가 아닌 사용자의 요청
   * @throws BusinessException REFUND_REQUEST_ALREADY_EXISTS - 이미 환불 요청이 존재하는 주문
   * @throws BusinessException INVALID_ORDER_STATUS - SHIPPING이 아닌 주문의 환불 요청
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CreateRefundRequestResponse createRefundRequest(
      Long buyerId, Long orderId, String reason) {
    String normalizedReason = normalizeReason(reason);

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () -> refundLockedCommandService.createRefundRequest(buyerId, orderId, normalizedReason));
  }

  /**
   * 로그인한 판매자가 REQUESTED 환불 요청을 승인하고 주문, 상품, 결제를 환불 결과로 전이한다.
   *
   * <p>공통 주문 분산락으로 동일 환불 요청 승인/거절을 직렬화하고, 상품이 ON_SALE로 복귀하면 검색 캐시 무효화 이벤트를 발행한다.
   *
   * @throws BusinessException REFUND_REQUEST_NOT_FOUND - 환불 요청이 존재하지 않는 경우
   * @throws BusinessException REFUND_REQUEST_ACCESS_DENIED - 주문 판매자가 아닌 사용자의 요청
   * @throws BusinessException INVALID_REFUND_REQUEST_STATUS - REQUESTED가 아닌 환불 요청 처리 또는 동시 처리 경합
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ApproveRefundRequestResponse approveRefundRequest(Long sellerId, Long refundId) {
    Long orderId = findOrderIdByRefundId(refundId);
    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () -> refundLockedCommandService.approveRefundRequest(sellerId, refundId));
  }

  /**
   * 로그인한 판매자가 REQUESTED 환불 요청을 거절하고 주문과 환불 요청을 DISPUTED로 전이한다.
   *
   * <p>공통 주문 분산락으로 동일 환불 요청 승인/거절을 직렬화한다. 거절 사유는 선택값이며 입력되면 trim 후 저장한다. 상품과 결제 상태는 변경하지 않는다.
   *
   * @throws BusinessException VALIDATION_FAILED - trim한 거절 사유가 255자를 초과하는 경우
   * @throws BusinessException REFUND_REQUEST_NOT_FOUND - 환불 요청이 존재하지 않는 경우
   * @throws BusinessException REFUND_REQUEST_ACCESS_DENIED - 주문 판매자가 아닌 사용자의 요청
   * @throws BusinessException INVALID_REFUND_REQUEST_STATUS - REQUESTED가 아닌 환불 요청 처리 또는 동시 처리 경합
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public RejectRefundRequestResponse rejectRefundRequest(
      Long sellerId, Long refundId, String reason) {
    String normalizedReason = normalizeOptionalReason(reason);

    Long orderId = findOrderIdByRefundId(refundId);
    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () -> refundLockedCommandService.rejectRefundRequest(sellerId, refundId, normalizedReason));
  }

  /**
   * 로그인한 판매자가 DISPUTED 환불 요청을 환불 또는 거래 완료 방향으로 종료한다.
   *
   * <p>공통 주문 분산락으로 동일 분쟁 종료 요청을 직렬화한다. 환불 종료는 결제를 REFUNDED로, 거래 완료 종료는 결제를 PAID로 유지하며, 양쪽 모두 상품 상태
   * 변경 후 검색 캐시 무효화 이벤트를 발행한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 종료 방향이 누락되었거나 trim한 종료 사유가 255자를 초과하는 경우
   * @throws BusinessException REFUND_REQUEST_NOT_FOUND - 환불 요청이 존재하지 않는 경우
   * @throws BusinessException REFUND_REQUEST_ACCESS_DENIED - 주문 판매자가 아닌 사용자의 요청
   * @throws BusinessException INVALID_REFUND_REQUEST_STATUS - DISPUTED 환불 요청 또는 DISPUTED 주문이 아닌 경우,
   *     또는 동시 처리 경합
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ResolveRefundRequestResponse resolveRefundRequest(
      Long sellerId, Long refundId, RefundResolution resolution, String reason) {
    if (resolution == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedReason = normalizeOptionalReason(reason);
    Long orderId = findOrderIdByRefundId(refundId);
    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () ->
            refundLockedCommandService.resolveRefundRequest(
                sellerId, refundId, resolution, normalizedReason));
  }

  private String normalizeReason(String reason) {
    if (reason == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedReason = reason.trim();
    if (normalizedReason.isBlank() || normalizedReason.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedReason;
  }

  private String normalizeOptionalReason(String reason) {
    if (reason == null) {
      return null;
    }
    String normalizedReason = reason.trim();
    if (normalizedReason.isBlank()) {
      return null;
    }
    if (normalizedReason.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedReason;
  }

  private Long findOrderIdByRefundId(Long refundId) {
    return refundRequestRepository
        .findOrderIdById(refundId)
        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }
}
