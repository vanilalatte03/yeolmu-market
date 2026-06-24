# ADR-006: 상품 숨김 여부 hidden 컬럼 사용

- 상태: 채택됨
- 날짜: 2026-06-24
- 관련: `docs/PRD.md` (상품 노출 상태), `docs/API.md` (관리자 상품 숨김), `docs/ERD.md` (product.hidden)
- 대체: ADR-003

## 맥락

ADR-003에서는 상품 거래 상태와 노출 상태를 분리하기 위해 `ProductVisibility` enum과 `visibility` 개념을 도입하기로 했다.

하지만 ERD와 구현 관점에서는 상품 노출 여부가 `product.hidden` boolean 하나로 충분하다.
숨김은 거래 상태가 아니라 관리자 노출 차단 여부이며, 값도 숨김/노출 두 가지뿐이다.
별도 enum을 두면 API 응답, Entity 필드, 문서 표현이 `visibility`와 `hidden`으로 갈라질 수 있다.

## 검토한 대안

- **`ProductVisibility` enum 유지** — 의미는 명확하지만 `VISIBLE`/`HIDDEN` 두 값만 위해 별도 enum과 API 필드를 유지해야 한다.
- **`hidden` boolean 사용** — ERD와 API가 단순해지고 숨김 여부를 직접 표현할 수 있다. 다만 `true`/`false` 의미를 문서와 DTO 이름에서 명확히 해야 한다.
- **`ProductStatus`에 `HIDDEN` 추가** — 구현은 단순하지만 숨김 처리 시 판매 중/예약 중/판매 완료 같은 거래 상태를 잃는다.

## 결정

상품 숨김 여부는 `hidden` boolean으로 관리한다.

`ProductStatus`는 거래 상태만 표현하며 `ON_SALE`, `RESERVED`, `SOLD_OUT`, `DELETED`를 가진다.
관리자 숨김/숨김 해제는 `status`를 변경하지 않고 `hidden` 값만 변경한다.

일반 상품 목록과 검색은 `hidden=false`이고 `status != DELETED`인 상품만 반환한다.
숨긴 상품 조회는 `hidden=true`인 상품을 대상으로 한다.

## 결과

- API 문서에서 `ProductVisibility` enum과 `visibility` 응답 필드를 제거한다.
- 관리자 상품 숨김 상태 변경 API는 `hidden` boolean을 요청/응답에 사용한다.
- ERD의 `product.hidden`이 상품 노출 여부의 정본이 된다.
- ADR-003의 `ProductVisibility` 결정은 이 ADR로 대체된다.
