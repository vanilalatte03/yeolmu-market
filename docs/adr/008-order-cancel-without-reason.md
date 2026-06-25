# ADR-008: 주문 취소 요청 사유 제외

- 상태: 채택됨
- 날짜: 2026-06-25
- 관련: GitHub Issue #48, `docs/PRD.md` (주문), `docs/API.md` (주문 API > 주문 취소), `docs/ERD.md` (orders, payment), `docs/adr/005-mock-safe-payment-transaction-policy.md`

## 맥락

P0 주문 취소는 결제나 배송으로 진행되기 전의 예약 주문을 구매자가 취소하고, 예약된 상품을 다시 판매 중 상태로 돌리는 기능이다.
초기 논의에서는 주문 취소 요청에 선택값 `reason`을 받을 수 있게 하되 P0에서는 저장하지 않는 방안이 있었다.
하지만 저장하지 않는 값을 API에서 받으면 클라이언트가 취소 사유가 기록되거나 운영 확인에 쓰인다고 오해할 수 있고, 실제 구현에서도 불필요한 Request DTO와 검증 분기가 생긴다.

주문 취소는 결제 취소와 다른 유스케이스다.
결제 취소의 `reason`과 `payment.cancel_reason`은 P1 모의 결제 취소 흐름에서 별도로 다루며, P0 주문 취소 요청 사양에 끌어오지 않는다.

## 검토한 대안

- **주문 취소 요청에서 `reason`을 선택값으로 받고 저장하지 않음** — 스키마 변경은 피할 수 있지만, 받기만 하고 버리는 필드라 API 의미가 불명확하다.
- **`orders.cancel_reason`을 추가해 주문 취소 사유를 저장함** — 감사 추적에는 도움이 될 수 있으나 P0 범위를 키우고, 현재 제품 요구사항에 없는 입력을 강제하거나 유도한다.
- **주문 취소 요청 body를 받지 않음** — P0 요구사항에 맞게 가장 단순하고, 취소 사유를 수집하지 않는다는 계약이 명확하다.

## 결정

`POST /api/orders/{orderId}/cancel`은 요청 body를 받지 않는다.
주문 취소 사유는 입력받지 않고, 저장하지 않고, 응답에도 노출하지 않는다.
따라서 주문 취소용 Request DTO에는 `reason`을 두지 않으며, `orders` 테이블에 `canceled_at` 또는 `cancel_reason` 컬럼을 추가하지 않는다.

취소 시각 응답이 필요하면 별도 주문 취소 컬럼을 추가하지 않고, 취소 상태 변경으로 갱신된 주문 `modifiedAt`을 UTC ISO-8601 형식의 `canceledAt`으로 반환한다.
결제 취소 API와 `payment.cancel_reason` 정책은 이 결정의 대상이 아니며 기존 결제 문서와 ADR-005를 따른다.

## 결과

- API 문서의 주문 취소 Request Body는 `없음`으로 유지한다.
- 주문 취소 구현은 body 없이 호출되는 요청을 정상 처리해야 한다.
- 주문 취소 Controller / Service 테스트는 `reason` 입력 성공 케이스를 요구하지 않는다.
- ERD의 `orders`에는 주문 취소 사유나 주문 취소 전용 시각 컬럼을 추가하지 않는다.
- ERD의 `payment.cancel_reason`은 결제 취소 사유 컬럼이므로 유지한다.
