# P1 테스트 시나리오

이 문서는 `docs/PRD.md`의 P1 범위를 기준으로 핵심 거래 흐름과 주요 상태 전이를 수동/반자동으로 검증하기 위한 시나리오를 정리한다.
-REST API는 [http/p1-api-flow.http](../http/p1-api-flow.http)로 위에서 아래 순서대로 실행한다.
P0 인증, 상품, 주문 기본 흐름은 [docs/P0_TEST_SCENARIOS.md](./P0_TEST_SCENARIOS.md)에서 별도 검증한다.

## 실행 전제

- 로컬 MySQL과 Redis가 실행되어 있어야 한다.
- 애플리케이션은 `http://localhost:8080`에서 실행 중이어야 한다.
- 상품 등록에 사용할 카테고리가 1개 이상 존재해야 한다. 없으면 관리자 카테고리 API 또는 DB seed로 먼저 생성한다.
- HTTP 응답은 공통 wrapper 기준으로 `success=true`, `code=SUCCESS`를 기본 성공 조건으로 본다.
- 각 실행은 고유 이메일과 `Idempotency-Key`를 사용한다.
- 시간 값은 정확한 현재 시각이 아니라 존재 여부와 UTC offset 형식(`Z` 또는 `+00:00`) 중심으로 확인한다.

## 핵심 흐름

| ID | 영역 | 시나리오 | 기대 결과 |
| --- | --- | --- | --- |
| P1-E2E-01 | 인증 | 판매자, 구매자, 타인이 회원가입하고 로그인한다. | 회원가입은 `201`, 로그인은 `200`; 각 로그인 응답에 access token, refresh token, userId가 있다. |
| P1-E2E-02 | 카테고리 | 카테고리 목록을 조회해 상품 등록에 사용할 categoryId를 준비한다. | `200`; 카테고리가 1개 이상이고 이후 상품 등록 요청의 `categoryId`로 사용할 수 있다. |
| P1-E2E-03 | 상품 | 판매자가 카테고리를 지정해 상품을 등록하고 카테고리별 상품 목록을 조회한다. | 상품 등록은 `201`, 상태는 `ON_SALE`; 카테고리별 상품 목록에 해당 상품이 포함된다. |
| P1-E2E-04 | 찜 | 구매자가 상품을 찜하고 내 찜 목록을 조회한다. | 찜 등록은 `201`; `wished=true`, `wishCount=1`; 내 찜 목록에 상품이 포함된다. |
| P1-E2E-05 | 찜 | 구매자가 상품 찜을 취소하고 내 찜 목록을 다시 조회한다. | 찜 취소는 `200`; `wished=false`, `wishCount=0`; 내 찜 목록에서 제거된다. |
| P1-E2E-06 | 주문 | 구매자가 상품을 주문한다. | `201`; 주문 상태는 `CREATED`; 상품 상태는 `RESERVED`가 된다. |
| P1-E2E-07 | 결제 | 구매자가 모의 결제 성공을 요청한다. | 첫 요청은 `201`; 결제 상태는 `PAID`, 주문 상태는 `PAID`, 상품 상태는 `RESERVED`다. |
| P1-E2E-08 | 결제 | 같은 주문에 같은 `Idempotency-Key`로 결제를 재요청한다. | `200`; 새 결제를 만들지 않고 기존 paymentId와 `PAID` 결과를 반환한다. |
| P1-E2E-09 | 배송 | 판매자가 배송 증빙을 등록한다. | `200`; 주문 상태는 `SHIPPING`, 송장 번호와 `shippedAt`이 반환된다. |
| P1-E2E-10 | 구매확정 | 구매자가 배송 중 주문을 구매확정한다. | `200`; 주문 상태는 `COMPLETED`, 상품 상태는 `SOLD_OUT`, 결제 상태는 `PAID`를 유지한다. |
| P1-E2E-11 | 조회 | 주문 상세, 상품 상세, 결제 상태를 조회한다. | 주문은 `COMPLETED`, 상품은 `SOLD_OUT`, 결제는 `PAID`로 조회된다. |

## 상태 전이/권한 실패 케이스

| ID | 영역 | 시나리오 | 기대 HTTP |
| --- | --- | --- | --- |
| P1-NEG-01 | 배송 | `CREATED` 주문에 판매자가 배송 증빙을 등록한다. | `409 INVALID_ORDER_STATUS` |
| P1-NEG-02 | 구매확정 | `CREATED` 주문에 구매자가 구매확정한다. | `409 INVALID_ORDER_STATUS` |
| P1-NEG-03 | 구매확정 | `PAID` 주문에 구매자가 구매확정한다. | `409 INVALID_ORDER_STATUS` |
| P1-NEG-04 | 결제 | 같은 주문에 다른 `Idempotency-Key`로 결제를 재요청한다. | `409 PAYMENT_ALREADY_EXISTS` |
| P1-NEG-05 | 배송 | 주문 판매자가 아닌 사용자가 배송 증빙을 등록한다. | `403 ORDER_ACCESS_DENIED` |
| P1-NEG-06 | 주문 | `SHIPPING` 주문에 구매자가 단순 주문 취소를 요청한다. | `409 INVALID_ORDER_STATUS` |
| P1-NEG-07 | 구매확정 | 주문 구매자가 아닌 사용자가 구매확정한다. | `403 ORDER_ACCESS_DENIED` |

## 결제 멱등키 검증 메모

1. 주문 생성 직후 새 `Idempotency-Key`로 `POST /api/orders/{orderId}/payment`를 호출한다.
2. 첫 요청은 `201 Created`이며 `paymentId`가 저장된다.
3. 같은 주문에 같은 `Idempotency-Key`로 재요청하면 `200 OK`와 기존 `paymentId`가 반환된다.
4. 같은 주문에 다른 `Idempotency-Key`로 재요청하면 `409 PAYMENT_ALREADY_EXISTS`가 반환된다.
5. 이 과정에서 주문은 `PAID`, 상품은 `RESERVED`, 결제는 `PAID` 상태를 유지해야 한다.

## 데이터 격리 메모

- `http/p1-api-flow.http`는 `runId`를 이메일과 멱등키에 포함해 반복 실행 간 충돌을 줄인다.
- 카테고리는 공유 데이터로 사용한다. 테스트 DB를 초기화하지 않는 환경에서는 동일 카테고리를 여러 실행에서 재사용해도 된다.
- 정상 플로우는 하나의 주문을 사용한다. 실패 요청은 상태를 변경하지 않는 정책을 전제로 배치되어 있다.
- 배송 증빙 등록 이후 단순 주문 취소는 실패해야 하며, 환불 요청/분쟁 흐름은 P2 범위에서 별도 검증한다.
