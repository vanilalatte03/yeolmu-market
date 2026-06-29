# P2 테스트 시나리오

이 문서는 `docs/PRD.md`의 P2 범위를 기준으로 배송 후 환불/분쟁 흐름과 거래 완료 후 리뷰/평점 흐름을 수동/반자동으로 검증하기 위한 시나리오를 정리한다.
REST API는 [http/p2-api-flow.http](../http/p2-api-flow.http)로 위에서 아래 순서대로 실행한다.
P1 모의 결제, 배송 증빙, 구매확정 기본 흐름은 [docs/P1_TEST_SCENARIOS.md](./P1_TEST_SCENARIOS.md)에서 별도 검증한다.

## 실행 전제

- 로컬 MySQL과 Redis가 실행되어 있어야 한다.
- 애플리케이션은 `http://localhost:8080`에서 실행 중이어야 한다.
- 상품 등록에 사용할 카테고리가 1개 이상 존재해야 한다. 없으면 관리자 카테고리 API 또는 DB seed로 먼저 생성한다.
- HTTP 응답은 공통 wrapper 기준으로 `success=true`, `code=SUCCESS`를 기본 성공 조건으로 본다.
- 각 실행은 고유 이메일과 `Idempotency-Key`를 사용한다.
- 시간 값은 정확한 현재 시각이 아니라 존재 여부와 UTC offset 형식(`Z` 또는 `+00:00`) 중심으로 확인한다.

## 정상 흐름

| ID | 영역 | 시나리오 | 기대 결과 |
| --- | --- | --- | --- |
| P2-E2E-01 | 인증 | 판매자, 구매자, 타인이 회원가입하고 로그인한다. | 회원가입은 `201`, 로그인은 `200`; 각 로그인 응답에 access token, refresh token, userId가 있다. |
| P2-E2E-02 | 준비 | 카테고리 조회 후 판매자가 상품을 등록하고 구매자가 주문, 결제, 배송 중 상태까지 진행한다. | 주문은 `SHIPPING`, 상품은 `RESERVED`, 결제는 `PAID`다. |
| P2-E2E-03 | 환불 승인 | 구매자가 `SHIPPING` 주문에 환불을 요청하고 판매자가 승인한다. | 환불 요청은 `APPROVED`, 주문은 `REFUNDED`, 결제는 `REFUNDED`, 상품은 `ON_SALE`이 된다. |
| P2-E2E-04 | 분쟁 REFUND | 구매자가 환불을 요청하고 판매자가 거절한 뒤, 판매자가 분쟁을 `REFUND`로 종료한다. | 거절 직후 주문과 환불 요청은 `DISPUTED`; 종료 후 환불 요청은 `CLOSED`, 주문은 `REFUNDED`, 결제는 `REFUNDED`, 상품은 `ON_SALE`이 된다. |
| P2-E2E-05 | 분쟁 COMPLETE | 구매자가 환불을 요청하고 판매자가 거절한 뒤, 판매자가 분쟁을 `COMPLETE`로 종료한다. | 종료 후 환불 요청은 `CLOSED`, 주문은 `COMPLETED`, 결제는 `PAID`, 상품은 `SOLD_OUT`이 된다. |
| P2-E2E-06 | 리뷰 작성 | P1 거래 완료 흐름으로 `COMPLETED` 주문을 만든 뒤 구매자와 판매자가 서로 리뷰를 작성한다. | 각 리뷰는 `201`; 리뷰어와 리뷰 대상은 주문 상대방으로 계산된다. |
| P2-E2E-07 | 리뷰 조회 | 내 작성 리뷰, 내 받은 리뷰, 특정 유저가 받은 공개 리뷰를 조회한다. | 각 목록에서 작성된 리뷰가 조회되고, 공개 목록에는 `orderId`가 노출되지 않는다. |
| P2-E2E-08 | 평점 집계 | 유저 상세와 상품 상세를 조회한다. | `averageRating`, `reviewCount`, `seller.averageRating`은 받은 리뷰 기준으로 계산된다. |
| P2-E2E-09 | 리뷰 수정/삭제 | 작성자가 자신의 리뷰를 수정하고 삭제한다. | 수정 응답은 변경된 `score`, `content`를 반환하고, 삭제 응답은 `deleted=true`를 반환한다. |

## 실패 흐름

| ID | 영역 | 시나리오 | 기대 HTTP |
| --- | --- | --- | --- |
| P2-NEG-01 | 환불 요청 | `SHIPPING`이 아닌 주문에서 환불을 요청한다. | `409 INVALID_ORDER_STATUS` |
| P2-NEG-02 | 환불 요청 | 주문 구매자가 아닌 사용자가 환불을 요청한다. | `403 ORDER_ACCESS_DENIED` |
| P2-NEG-03 | 환불 요청 | 같은 주문에 환불 요청을 중복 생성한다. | `409 REFUND_REQUEST_ALREADY_EXISTS` |
| P2-NEG-04 | 환불 처리 | 주문 판매자가 아닌 사용자가 환불 요청을 승인하거나 거절한다. | `403 REFUND_REQUEST_ACCESS_DENIED` |
| P2-NEG-05 | 환불 처리 | `REQUESTED`가 아닌 환불 요청을 승인하거나 거절한다. | `409 INVALID_REFUND_REQUEST_STATUS` |
| P2-NEG-06 | 분쟁 종료 | `DISPUTED`가 아닌 환불 요청을 종료한다. | `409 INVALID_REFUND_REQUEST_STATUS` |
| P2-NEG-07 | 분쟁 종료 | 주문 판매자가 아닌 사용자가 분쟁을 종료한다. | `403 REFUND_REQUEST_ACCESS_DENIED` |
| P2-NEG-08 | 분쟁 종료 | 잘못된 `resolution`으로 분쟁을 종료한다. | `400 VALIDATION_FAILED` |
| P2-NEG-09 | 리뷰 작성 | `COMPLETED` 전 주문에 리뷰를 작성한다. | `409 REVIEW_NOT_ALLOWED` |
| P2-NEG-10 | 리뷰 작성 | 주문 참여자가 아닌 사용자가 리뷰를 작성한다. | `403 ORDER_ACCESS_DENIED` |
| P2-NEG-11 | 리뷰 작성 | 같은 주문에 같은 작성자가 중복 리뷰를 작성한다. | `409 REVIEW_ALREADY_EXISTS` |
| P2-NEG-12 | 리뷰 수정/삭제 | 작성자가 아닌 사용자가 리뷰를 수정하거나 삭제한다. | `403 REVIEW_ACCESS_DENIED` |
| P2-NEG-13 | 리뷰 요청값 | 잘못된 평점, 빈 리뷰 내용, 잘못된 내 리뷰 목록 `status`를 보낸다. | `400 VALIDATION_FAILED` |

## 기대 상태

| 흐름 | 주문 | 결제 | 상품 | 환불 요청 | 리뷰/평점 |
| --- | --- | --- | --- | --- | --- |
| 환불 승인 | `REFUNDED` | `REFUNDED` | `ON_SALE` | `APPROVED` | 변경 없음 |
| 분쟁 `REFUND` 종료 | `REFUNDED` | `REFUNDED` | `ON_SALE` | `CLOSED` | 변경 없음 |
| 분쟁 `COMPLETE` 종료 | `COMPLETED` | `PAID` | `SOLD_OUT` | `CLOSED` | 이후 리뷰 작성 가능 |
| 리뷰 양방향 작성 | `COMPLETED` | `PAID` | `SOLD_OUT` | 없음 | 받은 리뷰 기준 평균과 개수가 갱신됨 |
| 리뷰 삭제 | 변경 없음 | 변경 없음 | 변경 없음 | 없음 | 삭제된 리뷰는 평점 집계에서 제외됨 |

## 자동화 테스트

- `src/test/java/com/guingujig/yeolmumarket/integration/P2RefundDisputeFlowIntegrationTest.java`
- `src/test/java/com/guingujig/yeolmumarket/integration/P2ReviewRatingFlowIntegrationTest.java`

실행 명령:

```bash
./gradlew test --tests "*P2RefundDisputeFlowIntegrationTest"
./gradlew test --tests "*P2ReviewRatingFlowIntegrationTest"
./gradlew test
./gradlew spotlessCheck
```

## 데이터 격리 메모

- 통합 테스트는 테스트 트랜잭션 롤백과 고유 이메일, 고유 `Idempotency-Key`를 사용한다.
- `http/p2-api-flow.http`는 `runId`를 이메일, 상품명, 결제 멱등키에 포함해 반복 실행 간 충돌을 줄인다.
- 환불 승인, 분쟁 `REFUND`, 분쟁 `COMPLETE`, 리뷰/평점은 서로 다른 주문을 사용한다.
- 환불/분쟁 실패 요청은 상태를 변경하지 않아야 하며, 후속 정상 요청이 같은 상태 전이 정책으로 동작해야 한다.
