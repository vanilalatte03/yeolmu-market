# P0 테스트 시나리오

이 문서는 `docs/PRD.md`의 P0 범위를 기준으로 출시 전 수동/반자동 검증에 사용할 시나리오를 정리한다.
REST API는 [http/p0-api-flow.http](../http/p0-api-flow.http)로 실행하고, WebSocket과 동시성은 별도 클라이언트 또는 자동화 테스트로 확인한다.

## 실행 전제

- 로컬 MySQL과 Redis가 실행되어 있어야 한다.
- 애플리케이션은 `http://localhost:8080`에서 실행 중이어야 한다.
- 관리자 상품 숨김 검증은 `ADMIN` 권한 계정의 access token이 필요하다.
- HTTP 응답은 공통 wrapper 기준으로 `success=true`, `code=SUCCESS`를 기본 성공 조건으로 본다.

## 핵심 흐름

| ID | 영역 | 시나리오 | 기대 결과 |
| --- | --- | --- | --- |
| P0-E2E-01 | 인증 | 판매자, 구매자1, 구매자2가 회원가입하고 로그인한다. | 회원가입은 `201`, 로그인은 `200`; 각 로그인 응답에 access token, refresh token, userId가 있다. |
| P0-E2E-02 | 유저 | 로그인한 사용자가 자신의 공개 프로필을 조회하고 닉네임을 수정한다. | 공개 조회는 `200`; 내 정보 수정은 `200`이고 변경된 nickname이 반환된다. |
| P0-E2E-03 | 상품 | 판매자가 상품을 등록한다. | `201`; 상품 상태는 `ON_SALE`; seller.userId가 판매자와 일치한다. |
| P0-E2E-04 | 상품 | 비회원이 상품 목록과 상세를 조회한다. | `200`; 삭제/숨김 상품은 일반 조회 결과에 노출되지 않는다. |
| P0-E2E-05 | 검색 | 구매자가 키워드, 가격 범위, 상태 조건으로 상품을 검색한다. | `200`; 조건에 맞는 상품만 반환된다. |
| P0-E2E-06 | 관리자 | 관리자가 상품을 숨김 처리하고 숨김 상품 목록을 조회한다. | 숨김 처리는 `200`; 숨김 상품은 일반 목록/검색에 노출되지 않고 관리자 숨김 목록에는 보인다. |
| P0-E2E-07 | 채팅 REST | 구매자가 상품 채팅방을 생성하고 내 채팅방 목록을 조회한다. | 채팅방 생성은 `201`; 같은 구매자/판매자/상품 조합으로 재요청해도 하나의 roomId가 유지된다. |
| P0-E2E-08 | 채팅 WS | 구매자와 판매자가 `/ws`에 STOMP CONNECT 후 같은 방을 구독하고 메시지를 주고받는다. | 메시지가 `/sub/chat-rooms/{roomId}`로 실시간 수신되고, 이전 메시지 조회에 저장된 메시지가 보인다. |
| P0-E2E-09 | 주문 | 구매자1이 판매 중 상품을 주문한다. | `201`; 주문 상태는 `CREATED`; 상품 상세 상태는 `RESERVED`가 된다. |
| P0-E2E-10 | 주문 | 구매자2가 같은 상품을 주문한다. | `409`; `PRODUCT_NOT_ON_SALE` 또는 동일 의미의 충돌 에러가 반환된다. 성공 주문은 1건뿐이다. |
| P0-E2E-11 | 주문 | 구매자1이 주문 상세과 내 구매 목록을 조회하고, 판매자가 내 판매 목록을 조회한다. | 참여자만 `200`; 비참여자는 `403`; 목록에 생성된 주문이 포함된다. |
| P0-E2E-12 | 주문 | 구매자1이 주문을 취소한다. | `200`; 주문 상태는 `CANCELED`; 상품 상태는 `ON_SALE`로 돌아간다. |
| P0-E2E-13 | 인증 | refresh token으로 토큰을 재발급하고, 로그아웃 후 보호 API를 호출한다. | refresh는 `200`이며 토큰이 회전된다. 로그아웃 후 기존 access token 보호 API는 `401 REVOKED_TOKEN`이다. |

## 권한/검증 실패 케이스

| ID | 영역 | 시나리오 | 기대 HTTP |
| --- | --- | --- | --- |
| P0-NEG-01 | 인증 | 이메일 형식 오류 또는 비밀번호 8자 미만으로 회원가입한다. | `400 VALIDATION_FAILED` |
| P0-NEG-02 | 인증 | 같은 이메일로 다시 회원가입한다. | `409 EMAIL_ALREADY_EXISTS` |
| P0-NEG-03 | 인증 | 잘못된 비밀번호로 로그인한다. | `401 INVALID_LOGIN_CREDENTIALS` |
| P0-NEG-04 | 상품 | 토큰 없이 상품을 등록한다. | `401 UNAUTHORIZED` |
| P0-NEG-05 | 상품 | 구매자가 판매자 상품을 수정/삭제한다. | `403 PRODUCT_ACCESS_DENIED` |
| P0-NEG-06 | 상품 | 가격 0 이하 또는 제목 공백으로 상품을 등록/수정한다. | `400 VALIDATION_FAILED` |
| P0-NEG-07 | 관리자 | 일반 사용자가 상품 숨김 API를 호출한다. | `403 FORBIDDEN` |
| P0-NEG-08 | 주문 | 판매자가 자신의 상품을 주문한다. | `400 CANNOT_ORDER_OWN_PRODUCT` |
| P0-NEG-09 | 주문 | 주문 참여자가 아닌 사용자가 주문 상세/취소를 호출한다. | `403 ORDER_ACCESS_DENIED` |
| P0-NEG-10 | 채팅 | 판매자가 자신의 상품 채팅방을 생성한다. | `400 CANNOT_CHAT_OWN_PRODUCT` |
| P0-NEG-11 | 채팅 | 채팅방 참여자가 아닌 사용자가 이전 메시지를 조회하거나 구독/SEND 한다. | REST `403`, STOMP user error queue에 `CHAT_ROOM_ACCESS_DENIED` |
| P0-NEG-12 | 검색 | 잘못된 페이지/크기 또는 허용하지 않는 status를 전달한다. | `400` |

## 동시 주문 검증

1. 판매자가 새 상품을 등록한다.
2. 구매자1과 구매자2가 각각 로그인한다.
3. 두 클라이언트에서 `POST /api/products/{productId}/orders`를 최대한 동시에 실행한다.
4. 성공 응답 `201 CREATED`는 정확히 1건이어야 한다.
5. 실패 응답은 `409` 계열이어야 한다.
6. 상품 상세 상태는 `RESERVED`, 주문 목록에는 생성 주문 1건만 보여야 한다.

순차 실행으로는 "이미 예약된 상품 주문 방지"만 확인된다. 실제 동시성은 JMeter, k6, Postman Runner 병렬 실행, 또는 Spring 통합 테스트로 별도 확인한다.

## 검색 캐시 성능 검증

1. 동일한 검색 조건으로 `/api/search/products`와 `/api/search/v2/products`를 각각 2회 이상 호출한다.
2. 같은 조건의 결과 content와 page 정보가 동일한지 확인한다.
3. 캐시 대상인 v2 반복 호출의 응답 시간이 첫 호출보다 개선되는지 확인한다.
4. 상품 등록/수정/삭제/숨김 후 같은 검색 조건에서 오래된 결과가 노출되지 않는지 확인한다.

수동 HTTP 클라이언트의 응답 시간은 참고용이다. 완료 판정은 서비스 로그, Redis hit/miss, 또는 성능 테스트 결과로 보강한다.

## WebSocket STOMP 검증 메모

- CONNECT: `ws://localhost:8080/ws`, native header `Authorization: Bearer {accessToken}`
- 구매자/판매자 모두 `/sub/chat-rooms/{roomId}` 구독
- 사용자 오류 큐: `/user/queue/errors`
- SEND: `/pub/chat-rooms/{roomId}/message`
- payload: `{"content":"거래 가능할까요?"}`
- 이전 메시지 조회: `GET /api/chat-rooms/{roomId}/messages`
