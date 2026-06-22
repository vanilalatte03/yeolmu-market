# ADR-003: 상품 거래 상태와 노출 상태 분리

- 상태: 채택됨
- 날짜: 2026-06-22
- 관련: `docs/PRD.md` (7.2 상품, 8.1 상품 상태), `docs/API.md` (ProductStatus, ProductVisibility, 관리자 상품 숨김), `docs/ERD.md` (product)

## 맥락

상품 숨김을 `ProductStatus`의 `HIDDEN`으로 표현하면 상품의 거래 상태를 잃는다.
예를 들어 예약 중(`RESERVED`)이거나 판매 완료(`SOLD_OUT`)인 상품을 관리자가 숨긴 뒤 해제하면 원래 거래 상태로 복원할 기준이 없다.

## 검토한 대안

- **`ProductStatus`에 `HIDDEN` 유지** — 구현은 단순하지만 숨김 해제 시 원래 거래 상태를 복원할 수 없다.
- **숨김 전 상태를 별도 컬럼에 저장** — 기존 상태 enum을 유지할 수 있지만 상태 복원용 임시 컬럼과 전이 규칙이 늘어난다.
- **거래 상태와 노출 상태 분리** — 거래 상태는 주문 가능 여부를, 노출 상태는 일반 사용자 노출 여부를 표현한다.

## 결정

상품 거래 상태(`ProductStatus`)와 상품 노출 상태(`ProductVisibility`)를 분리한다.
`ProductStatus`는 `ON_SALE`, `RESERVED`, `SOLD_OUT`, `DELETED`를 가진다.
`ProductVisibility`는 `VISIBLE`, `HIDDEN`을 가진다.

관리자 숨김/숨김 해제는 `visibility`만 변경하고 `status`는 변경하지 않는다.

## 결과

- 일반 상품 목록과 검색은 `visibility=VISIBLE`이고 `status != DELETED`인 상품만 반환한다.
- 관리자 숨김 목록은 `visibility=HIDDEN`인 상품을 대상으로 한다.
- 주문 가능 여부는 `status=ON_SALE` 여부로 판단한다.
- 숨김 해제 후에도 예약 중, 판매 완료 등 기존 거래 상태가 유지된다.
