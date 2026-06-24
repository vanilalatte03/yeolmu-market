# 열무마켓 API 명세

이 문서는 현재 API 목록과 `docs/PRD.md` 기준의 API 계약 초안이다.
성공/실패 응답은 모두 [공통 응답](#공통-응답) wrapper를 사용한다.
아래 `Response Data` 예시는 wrapper의 `data` 안에 들어가는 값만 보여준다.

## 목차

- [공통 API 규칙](#공통-api-규칙)
- [API 목록](#api-목록)
- [Enum](#enum)
- [공통 에러 코드](#공통-에러-코드)
- [도메인 에러 코드 카탈로그](#도메인-에러-코드-카탈로그)
- [인증 API](#인증-api)
- [유저 API](#유저-api)
- [상품 API](#상품-api)
- [관리자 API](#관리자-api)
- [검색 API](#검색-api)
- [주문 API](#주문-api)
- [채팅 API](#채팅-api)
- [결제 API](#결제-api)
- [카테고리 API](#카테고리-api)
- [찜 API](#찜-api)
- [리뷰 API](#리뷰-api)
- [환불/분쟁 API](#환불분쟁-api)

## 공통 API 규칙

### Base URL

- 로컬 실행 기준: `http://localhost:8080`
- 모든 REST API prefix: `/api`
- REST 요청/응답 Content-Type: `application/json; charset=UTF-8`
- 이미지 업로드 API Content-Type: `multipart/form-data`
- 금액 단위: 원화 정수. 소수점 금액은 사용하지 않는다.
- 일시 형식: ISO 8601 문자열. 예: `2026-06-22T18:30:00+09:00`

### 인증

JWT Bearer 토큰을 사용한다.

```http
Authorization: Bearer {accessToken}
```

로그아웃된 access token은 Redis 블랙리스트에 등록한다.
블랙리스트 TTL은 JWT 만료까지 남은 시간으로 설정하며, 만료 시간이 지나면 Redis에서 자동 제거된다.
인증이 필요한 API는 토큰 서명과 만료 여부를 검증한 뒤 블랙리스트 등록 여부도 확인한다.
refresh token은 Redis에 활성 토큰 해시 또는 식별자를 저장해 서버에서 관리한다.
이번 프로젝트는 사용자별 활성 refresh token을 1개만 허용한다.
로그인 또는 refresh token 재발급 성공 시 기존 refresh token은 폐기되고 새 refresh token만 유효하다.
로그아웃 시 요청에 사용된 access token을 블랙리스트에 등록하고, 인증된 회원의 활성 refresh token도 Redis에서 삭제한다.
JWT 폐기와 refresh token 회전 정책은 `docs/adr/007-jwt-refresh-token-rotation.md`를 따른다.

인증이 필요 없는 API는 다음과 같다.

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/products`
- `GET /api/products/{productId}`
- `GET /api/users/{userId}`
- `GET /api/users/{userId}/products`
- `GET /api/search/products`
- `GET /api/search/popular-keywords`
- `GET /api/categories`
- `GET /api/categories/{categoryId}/products`
- `GET /api/products/{productId}/wishes/count`
- `GET /api/users/{userId}/reviews`
- `GET /api/users/{userId}/rating`

관리자 API는 `ADMIN` 권한이 필요하다.

### 공통 응답

성공과 실패 모두 같은 wrapper를 사용한다.
응답 body의 `success`는 API 처리 성공 여부를 나타낸다.
비즈니스/검증 실패 여부는 실제 HTTP 상태 코드와 `success`, `code`, `message`로 판단한다.

성공 응답:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패 응답:

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다.",
  "errors": [
    "email은 필수입니다."
  ]
}
```

`data` 또는 `errors`가 `null`이면 응답 JSON에서 생략한다.

### 페이지네이션

목록 API는 기본적으로 다음 query parameter를 사용한다.

| 이름 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `page` | int | `0` | 0부터 시작하는 페이지 번호 |
| `size` | int | `10` | 페이지 크기. 최대 `100` |

페이지 응답 형식:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 120,
  "totalPages": 12,
  "hasNext": true
}
```

## API 목록

| 우선순위 | 도메인 | 이름 | Method | Path |
| --- | --- | --- | --- | --- |
| P0 | 인증 | 회원가입 | `POST` | `/api/auth/signup` |
| P0 | 인증 | 로그인 | `POST` | `/api/auth/login` |
| P0 | 인증 | 로그아웃 | `POST` | `/api/auth/logout` |
| P0 | 인증 | 리프레시 토큰 | `POST` | `/api/auth/refresh` |
| P0 | 유저 | 유저 정보 조회 | `GET` | `/api/users/{userId}` |
| P0 | 유저 | 내정보 수정 | `PATCH` | `/api/users/me` |
| P0 | 상품 | 상품 등록 | `POST` | `/api/products` |
| P0 | 상품 | 상품 목록 조회 | `GET` | `/api/products` |
| P0 | 상품 | 상품 상세 조회 | `GET` | `/api/products/{productId}` |
| P0 | 상품 | 상품 수정 | `PUT` | `/api/products/{productId}` |
| P0 | 상품 | 상품 삭제 | `DELETE` | `/api/products/{productId}` |
| P0 | 상품 | 특정 유저의 판매 상품 목록 | `GET` | `/api/users/{userId}/products` |
| P0 | 상품 | 내 판매 상품 목록 | `GET` | `/api/users/me/products` |
| P0 | 검색 | 상품 검색 | `GET` | `/api/search/products` |
| P0 | 관리자 | 상품 숨김 상태 변경 | `PATCH` | `/api/admin/products/{productId}/hidden` |
| P0 | 관리자 | 숨긴 상품 조회 | `GET` | `/api/admin/products/hidden` |
| P0 | 검색 | 인기 검색어 조회 | `GET` | `/api/search/popular-keywords` |
| P0 | 주문 | 주문 생성 | `POST` | `/api/products/{productId}/orders` |
| P0 | 주문 | 주문 상세 조회 | `GET` | `/api/orders/{orderId}` |
| P0 | 주문 | 내 구매 주문 목록 | `GET` | `/api/users/me/orders` |
| P0 | 주문 | 내 판매 주문 목록 | `GET` | `/api/users/me/sales` |
| P0 | 주문 | 주문 취소 | `POST` | `/api/orders/{orderId}/cancel` |
| P0 | 채팅 | 채팅방 생성 | `POST` | `/api/products/{productId}/chat-rooms` |
| P0 | 채팅 | 내 채팅방 목록 조회 | `GET` | `/api/chat-rooms` |
| P0 | 채팅 | 채팅방 상세 조회 | `GET` | `/api/chat-rooms/{roomId}` |
| P0 | 채팅 | 이전 메시지 조회 | `GET` | `/api/chat-rooms/{roomId}/messages` |
| P0 | 채팅 | 웹소켓 연결 | `WS Connect` | `/ws` |
| P0 | 채팅 | 채팅방 메시지 구독 | `SUB` | `/sub/chat-rooms/{roomId}` |
| P0 | 채팅 | 메시지 전송 | `PUB` | `/pub/chat-rooms/{roomId}/message` |
| P1 | 상품 | 상품 이미지 업로드 | `POST` | `/api/products/{productId}/images` |
| P1 | 상품 | 상품 이미지 삭제 | `DELETE` | `/api/products/{productId}/images/{imageId}` |
| P1 | 결제 | 결제 요청 | `POST` | `/api/orders/{orderId}/payment` |
| P1 | 결제 | 결제 상태 조회 | `GET` | `/api/payments/{paymentId}/status` |
| P1 | 결제 | 결제 상세 조회 | `GET` | `/api/payments/{paymentId}` |
| P1 | 결제 | 결제 취소 | `POST` | `/api/payments/{paymentId}/cancel` |
| P1 | 결제 | 구매 확정 | `POST` | `/api/orders/{orderId}/confirm` |
| P1 | 카테고리 | 카테고리 목록 조회 | `GET` | `/api/categories` |
| P1 | 카테고리 | 카테고리 생성 | `POST` | `/api/admin/categories` |
| P1 | 카테고리 | 카테고리 수정 | `PUT` | `/api/admin/categories/{categoryId}` |
| P1 | 카테고리 | 카테고리 삭제 | `DELETE` | `/api/admin/categories/{categoryId}` |
| P1 | 카테고리 | 카테고리별 상품 조회 | `GET` | `/api/categories/{categoryId}/products` |
| P1 | 찜 | 찜하기 | `POST` | `/api/products/{productId}/wishes` |
| P1 | 찜 | 찜 취소 | `DELETE` | `/api/products/{productId}/wishes` |
| P1 | 찜 | 내가 찜한 상품 목록 | `GET` | `/api/users/me/wishes` |
| P1 | 찜 | 찜 여부 | `GET` | `/api/products/{productId}/wish-status` |
| P1 | 찜 | 상품 찜 수 조회 | `GET` | `/api/products/{productId}/wishes/count` |
| P1 | 주문 | 배송 증빙 등록 | `PATCH` | `/api/orders/{orderId}/shipping` |
| P2 | 리뷰 | 유저 리뷰 작성 | `POST` | `/api/orders/{orderId}/reviews` |
| P2 | 리뷰 | 유저 리뷰 수정 | `PATCH` | `/api/reviews/{reviewId}` |
| P2 | 리뷰 | 유저 리뷰 삭제 | `DELETE` | `/api/reviews/{reviewId}` |
| P2 | 리뷰 | 특정 유저가 받은 리뷰 목록 | `GET` | `/api/users/{userId}/reviews` |
| P2 | 리뷰 | 특정 유저의 평점 조회 | `GET` | `/api/users/{userId}/rating` |
| P2 | 리뷰 | 내가 작성한 리뷰 | `GET` | `/api/users/me/reviews/written` |
| P2 | 리뷰 | 내가 받은 리뷰 | `GET` | `/api/users/me/reviews/received` |
| P2 | 환불/분쟁 | 환불 요청 거절 | `POST` | `/api/refund-requests/{refundRequestId}/reject` |
| P2 | 환불/분쟁 | 환불요청 | `POST` | `/api/orders/{orderId}/refund-requests` |
| P2 | 환불/분쟁 | 환불 요청 승인 | `POST` | `/api/refund-requests/{refundRequestId}/approve` |
| P2 | 환불/분쟁 | 분쟁 종료 | `POST` | `/api/refund-requests/{refundRequestId}/resolve` |

## Enum

### UserRole

| 값 | 설명 |
| --- | --- |
| `USER` | 일반 회원 |
| `ADMIN` | 관리자 |

### ProductStatus

| 값 | 설명 |
| --- | --- |
| `ON_SALE` | 판매 중 |
| `RESERVED` | 예약 중 |
| `SOLD_OUT` | 판매 완료 |
| `DELETED` | 삭제 |

### OrderStatus

| 값 | 설명 |
| --- | --- |
| `CREATED` | 주문 생성 |
| `PAID` | 결제 완료 |
| `SHIPPING` | 배송 중 |
| `COMPLETED` | 거래 완료 |
| `CANCELED` | 취소 |
| `REFUND_REQUESTED` | 환불 요청 |
| `REFUNDED` | 환불 |
| `DISPUTED` | 분쟁 |

### PaymentStatus

| 값 | 설명 |
| --- | --- |
| `PENDING` | 결제 대기 |
| `PAID` | 결제 성공 |
| `FAILED` | 결제 실패 |
| `CANCELED` | 결제 취소 |
| `REFUNDED` | 환불(결제 후 취소 시 상태만 변경) |

### RefundRequestStatus

| 값 | 설명 |
| --- | --- |
| `REQUESTED` | 구매자가 환불을 요청한 상태 |
| `APPROVED` | 판매자가 환불 요청을 승인한 상태 |
| `DISPUTED` | 판매자가 환불 요청을 거절해 분쟁으로 전환된 상태 |
| `CLOSED` | 환불 또는 거래 완료로 요청 처리가 종료된 상태 |

환불 요청 상태 흐름은 `REQUESTED -> APPROVED`, `REQUESTED -> DISPUTED`, `DISPUTED -> CLOSED`만 허용한다.
판매자가 거절하면 별도 거절 상태를 두지 않고 곧바로 `DISPUTED`로 전환한다.

## 공통 에러 코드

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 요청 본문, 쿼리 파라미터, 경로 변수 검증 실패 |
| `INVALID_ENUM_VALUE` | 400 | 허용하지 않는 Enum 값 |
| `MISSING_REQUIRED_FIELD` | 400 | 필수 값 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |
| `UNAUTHORIZED` | 401 | 인증 토큰 누락 또는 인증 실패 |
| `INVALID_TOKEN` | 401 | 잘못된 JWT |
| `EXPIRED_TOKEN` | 401 | 만료된 JWT |
| `REVOKED_TOKEN` | 401 | 로그아웃 또는 refresh token 회전으로 폐기된 JWT |
| `FORBIDDEN` | 403 | 권한 또는 소유권 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 리소스 없음 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP method |
| `CONFLICT` | 409 | 현재 상태와 충돌하는 요청 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

## 도메인 에러 코드 카탈로그

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |
| `INVALID_LOGIN_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `USER_NOT_FOUND` | 404 | 회원 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `PRODUCT_ACCESS_DENIED` | 403 | 상품 수정, 삭제, 이미지 관리 권한 없음 |
| `PRODUCT_NOT_ON_SALE` | 409 | 판매 중이 아닌 상품 |
| `PRODUCT_HAS_ACTIVE_ORDER` | 409 | 거래 진행 중인 상품 삭제 시도 |
| `CANNOT_ORDER_OWN_PRODUCT` | 400 | 자신의 상품 주문 시도 |
| `IMAGE_NOT_FOUND` | 404 | 상품 이미지 없음 |
| `UNSUPPORTED_IMAGE_TYPE` | 400 | 지원하지 않는 이미지 형식 |
| `FILE_SIZE_EXCEEDED` | 400 | 업로드 파일 크기 초과 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 접근 |
| `INVALID_ORDER_STATUS` | 409 | 현재 주문 상태에서 수행할 수 없는 작업 |
| `ORDER_ALREADY_EXISTS` | 409 | 동일 상품에 이미 유효한 주문 존재 |
| `CHAT_ROOM_NOT_FOUND` | 404 | 채팅방 없음 |
| `CHAT_ROOM_ACCESS_DENIED` | 403 | 채팅방 참여자가 아님 |
| `MESSAGE_SEND_NOT_ALLOWED` | 403 | 메시지를 보낼 수 없는 채팅방 |
| `CANNOT_CHAT_OWN_PRODUCT` | 400 | 자신의 상품에 채팅방 생성 시도 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `PAYMENT_ACCESS_DENIED` | 403 | 타인의 결제 접근 |
| `PAYMENT_ALREADY_EXISTS` | 409 | 해당 주문의 결제가 이미 존재 |
| `INVALID_PAYMENT_STATUS` | 409 | 현재 결제 상태에서 수행할 수 없는 작업 |
| `REFUND_REQUEST_NOT_FOUND` | 404 | 환불 요청 없음 |
| `REFUND_REQUEST_ACCESS_DENIED` | 403 | 환불 요청 처리 권한 없음 |
| `REFUND_REQUEST_ALREADY_EXISTS` | 409 | 해당 주문의 환불 요청이 이미 존재 |
| `INVALID_REFUND_REQUEST_STATUS` | 409 | 현재 환불 요청 상태에서 수행할 수 없는 작업 |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리 없음 |
| `CATEGORY_NAME_ALREADY_EXISTS` | 409 | 이미 존재하는 카테고리명 |
| `CATEGORY_IN_USE` | 409 | 상품이 연결된 카테고리 삭제 시도 |
| `WISH_NOT_FOUND` | 404 | 찜 없음 |
| `WISH_ALREADY_EXISTS` | 409 | 이미 찜한 상품 |
| `REVIEW_NOT_FOUND` | 404 | 리뷰 없음 |
| `REVIEW_ACCESS_DENIED` | 403 | 리뷰 수정, 삭제 권한 없음 |
| `REVIEW_ALREADY_EXISTS` | 409 | 이미 작성한 주문 리뷰 |
| `REVIEW_NOT_ALLOWED` | 409 | 리뷰를 작성할 수 없는 주문 상태 |

## 인증 API

인증 API는 회원 계정 생성과 로그인 세션 관리를 담당한다.
회원가입, 로그인, 리프레시 토큰 발급은 access token 없이 호출하고, 로그아웃은 인증된 회원만 호출한다.

### 회원가입

회원을 생성한다.

- Method: `POST`
- Path: `/api/auth/signup`
- 인증: 불필요
- HTTP Status: `201 Created`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 로그인 이메일. UNIQUE |
| `password` | String | Y | 비밀번호. 서버에서 암호화 저장 |
| `nickname` | String | Y | 서비스에서 노출되는 닉네임 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!",
  "nickname": "열무구매자"
}
```

#### Response Data

```json
{
  "userId": 1,
  "email": "customer@example.com",
  "nickname": "열무구매자",
  "role": "USER",
  "createdAt": "2026-06-22T18:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일 형식 오류, 비밀번호 정책 위반, 닉네임 누락 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |

### 로그인

이메일과 비밀번호를 검증하고 JWT access token과 refresh token을 발급한다.
서버는 발급한 refresh token의 해시 또는 식별자를 Redis에 저장한다.
이미 활성 refresh token이 있으면 새 refresh token으로 교체하고 기존 refresh token은 폐기한다.

- Method: `POST`
- Path: `/api/auth/login`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 로그인 이메일 |
| `password` | String | Y | 비밀번호 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!"
}
```

#### Response Data

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "refreshExpiresIn": 1209600,
  "user": {
    "userId": 1,
    "email": "customer@example.com",
    "nickname": "열무구매자",
    "role": "USER"
  }
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일 누락 또는 형식 오류, 비밀번호 누락 |
| `INVALID_LOGIN_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |

### 로그아웃

로그아웃을 처리한다.
서버는 요청에 사용된 access token을 Redis 블랙리스트에 등록하고, JWT 만료까지 남은 시간을 TTL로 설정한다.
서버는 인증된 회원의 활성 refresh token도 Redis에서 삭제한다.
클라이언트는 성공 응답을 받은 뒤 보관 중인 access token과 refresh token을 삭제한다.
블랙리스트에 등록된 토큰은 만료 전이라도 보호된 API 인증에 사용할 수 없다.
삭제된 refresh token은 만료 전이라도 재발급에 사용할 수 없다.

- Method: `POST`
- Path: `/api/auth/logout`
- 인증: 필요
- HTTP Status: `200 OK`

#### Request Body

없음

#### Response Data

```json
{
  "loggedOut": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_TOKEN` | 401 | 잘못된 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |
| `REVOKED_TOKEN` | 401 | 이미 로그아웃되어 폐기된 토큰 |

### 리프레시 토큰

refresh token을 검증하고 새 access token을 발급한다.
서버는 요청 refresh token의 서명, 만료 시간, Redis에 저장된 활성 refresh token과의 일치 여부를 모두 확인한다.
재발급이 성공하면 기존 refresh token은 폐기하고 새 refresh token을 Redis에 저장한다.
이미 폐기되었거나 현재 활성 토큰과 일치하지 않는 refresh token은 인증 실패로 처리한다.

- Method: `POST`
- Path: `/api/auth/refresh`
- 인증: access token 불필요
- HTTP Status: `200 OK`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 로그인 시 발급받은 refresh token |

```json
{
  "refreshToken": "eyJhbGciOi..."
}
```

#### Response Data

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "refreshExpiresIn": 1209600
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | refresh token 누락 |
| `INVALID_TOKEN` | 401 | 잘못된 refresh token |
| `EXPIRED_TOKEN` | 401 | 만료된 refresh token |
| `REVOKED_TOKEN` | 401 | 폐기되었거나 현재 활성 토큰이 아닌 refresh token |

## 유저 API

유저 API는 공개 프로필 조회와 로그인한 사용자의 계정 정보 수정을 담당한다.

### 유저 정보 조회

특정 유저의 공개 정보를 조회한다.

- Method: `GET`
- Path: `/api/users/{userId}`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | Long | 조회할 유저 ID |

#### Response Data

```json
{
  "userId": 1,
  "nickname": "열무구매자",
  "role": "USER",
  "createdAt": "2026-06-22T18:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `USER_NOT_FOUND` | 404 | 회원 없음 |

### 내정보 수정

로그인한 사용자의 닉네임 또는 비밀번호를 수정한다.

- Method: `PATCH`
- Path: `/api/users/me`
- 인증: 필요
- HTTP Status: `200 OK`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | String | N | 변경할 닉네임 |
| `password` | String | N | 변경할 비밀번호 |

```json
{
  "nickname": "열무판매자",
  "password": "NewPassword123!"
}
```

#### Response Data

```json
{
  "userId": 1,
  "email": "customer@example.com",
  "nickname": "열무판매자",
  "role": "USER",
  "updatedAt": "2026-06-22T18:35:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 수정할 값이 없거나 값 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `USER_NOT_FOUND` | 404 | 인증된 회원을 찾을 수 없음 |

## 상품 API

상품 API는 상품 등록, 조회, 수정, 삭제, 판매 상품 목록, 이미지 관리를 담당한다.
P1 이미지 기능을 제외한 기본 상품 기능은 P0 범위다.
상품 숨김 여부는 `hidden` boolean으로 관리하며, 관련 결정은 `docs/adr/006-product-hidden-flag.md`를 따른다.

### 상품 등록

로그인한 사용자가 상품을 등록한다.

- Method: `POST`
- Path: `/api/products`
- 인증: 필요
- HTTP Status: `201 Created`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String | Y | 상품명 |
| `description` | String | Y | 상품 설명 |
| `price` | int | Y | 판매 가격 |
| `categoryId` | Long | N | 카테고리 ID. P1부터 사용 |

```json
{
  "title": "아이패드 미니 6",
  "description": "생활기스 조금 있습니다.",
  "price": 450000,
  "categoryId": 3
}
```

#### 단계별 요청 필드

| 필드 | 도입 단계 |
| --- | --- |
| `title`, `description`, `price` | P0 |
| `categoryId` | P1 |

#### Response Data

```json
{
  "productId": 10,
  "title": "아이패드 미니 6",
  "description": "생활기스 조금 있습니다.",
  "price": 450000,
  "status": "ON_SALE",
  "seller": {
    "userId": 1,
    "nickname": "열무판매자"
  },
  "createdAt": "2026-06-22T18:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 상품명, 설명, 가격 검증 실패 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `CATEGORY_NOT_FOUND` | 404 | 존재하지 않는 카테고리. P1 `categoryId` 사용 시 |

### 상품 목록 조회

일반 사용자에게 노출 가능한 상품 목록을 조회한다.
숨김·삭제 상품은 공개 목록과 검색에 노출되지 않으며, 숨김 상품은 관리자 전용 `GET /api/admin/products/hidden`에서만 확인한다.
`thumbnailUrl`은 P1(상품 이미지) 도입 후 채워지며, 그 전에는 `null`이다.

- Method: `GET`
- Path: `/api/products`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `status` | ProductStatus | N | 상품 상태. 기본값 `ON_SALE`. 공개 목록은 `hidden=false`인 상품만 반환하며 `DELETED`는 조회 불가 |
| `sort` | String | N | 정렬 조건. 예: `latest`, `priceAsc`, `priceDesc` |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6",
      "price": 450000,
      "status": "ON_SALE",
      "thumbnailUrl": "https://cdn.example.com/products/10/thumbnail.jpg",
      "sellerNickname": "열무판매자",
      "createdAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### 단계별 응답 필드

P0에서는 `thumbnailUrl`이 `null`이다.

| 필드 | 도입 단계 |
| --- | --- |
| `content[].productId`, `content[].title`, `content[].price`, `content[].status`, `content[].sellerNickname`, `content[].createdAt`, `page`, `size`, `totalElements`, `totalPages`, `hasNext` | P0 |
| `content[].thumbnailUrl` | P1 |

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |
| `INVALID_ENUM_VALUE` | 400 | 허용하지 않는 상품 상태 |

### 상품 상세 조회

상품 상세 정보를 조회한다. P2 조회수 기능이 도입되면 상세 조회 시 조회수가 증가한다.

- Method: `GET`
- Path: `/api/products/{productId}`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Response Data

```json
{
  "productId": 10,
  "title": "아이패드 미니 6",
  "description": "생활기스 조금 있습니다.",
  "price": 450000,
  "status": "ON_SALE",
  "category": {
    "categoryId": 3,
    "name": "디지털기기"
  },
  "images": [
    {
      "imageId": 1,
      "url": "https://cdn.example.com/products/10/1.jpg",
      "thumbnail": true
    }
  ],
  "wishCount": 12,
  "viewCount": 101,
  "seller": {
    "userId": 1,
    "nickname": "열무판매자",
    "averageRating": 4.8
  },
  "createdAt": "2026-06-22T18:30:00+09:00",
  "updatedAt": "2026-06-22T18:30:00+09:00"
}
```

#### 단계별 필드

응답 필드는 도입 단계에 따라 추가된다. P0 응답에는 아래 P1, P2 필드가 포함되지 않는다.

| 필드 | 도입 단계 |
| --- | --- |
| `productId`, `title`, `description`, `price`, `status`, `seller.userId`, `seller.nickname`, `createdAt`, `updatedAt` | P0 |
| `category`, `images`, `wishCount` | P1 |
| `viewCount`, `seller.averageRating` | P2 |

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 또는 일반 사용자에게 노출되지 않는 상품 |

### 상품 수정

상품 판매자가 상품 정보를 수정한다.

- Method: `PUT`
- Path: `/api/products/{productId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String | N | 상품명 |
| `description` | String | N | 상품 설명 |
| `price` | int | N | 판매 가격 |
| `categoryId` | Long | N | 카테고리 ID. P1부터 사용 |

```json
{
  "title": "아이패드 미니 6세대",
  "description": "박스 포함입니다.",
  "price": 430000,
  "categoryId": 3
}
```

#### 단계별 요청 필드

| 필드 | 도입 단계 |
| --- | --- |
| `title`, `description`, `price` | P0 |
| `categoryId` | P1 |

#### Response Data

```json
{
  "productId": 10,
  "title": "아이패드 미니 6세대",
  "description": "박스 포함입니다.",
  "price": 430000,
  "status": "ON_SALE",
  "updatedAt": "2026-06-22T18:40:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 수정할 값이 없거나 값 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_ACCESS_DENIED` | 403 | 판매자가 아닌 사용자의 수정 시도 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |

### 상품 삭제

상품 판매자가 상품을 삭제한다. 삭제된 상품은 일반 목록과 검색 결과에 노출되지 않는다.

- Method: `DELETE`
- Path: `/api/products/{productId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Response Data

```json
{
  "deleted": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_ACCESS_DENIED` | 403 | 판매자가 아닌 사용자의 삭제 시도 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `PRODUCT_HAS_ACTIVE_ORDER` | 409 | 거래 진행 중인 상품 삭제 시도 |

### 특정 유저의 판매 상품 목록

특정 유저가 판매자로 등록한 공개 상품 목록을 조회한다.
숨김·삭제 상품은 반환하지 않으며, 공개 목록은 `hidden=false`이고 `status != DELETED`인 상품만 반환한다.

- Method: `GET`
- Path: `/api/users/{userId}/products`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | Long | 판매자 사용자 ID |

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `status` | ProductStatus | N | 상품 상태 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "price": 430000,
      "status": "ON_SALE",
      "createdAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `USER_NOT_FOUND` | 404 | 회원 없음 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 내 판매 상품 목록

로그인한 사용자의 판매 상품 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/products`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `status` | ProductStatus | N | 상품 상태 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "price": 430000,
      "status": "ON_SALE",
      "createdAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 상품 이미지 업로드

상품 판매자가 상품 이미지를 업로드한다.

- Method: `POST`
- Path: `/api/products/{productId}/images`
- 인증: 필요
- HTTP Status: `201 Created`
- Content-Type: `multipart/form-data`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Parts

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `images` | MultipartFile[] | Y | 업로드할 이미지 파일 목록 |

#### Response Data

```json
{
  "images": [
    {
      "imageId": 1,
      "url": "https://cdn.example.com/products/10/1.jpg",
      "thumbnail": true,
      "uploadedAt": "2026-06-22T18:30:00+09:00"
    }
  ]
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_ACCESS_DENIED` | 403 | 판매자가 아닌 사용자의 업로드 시도 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `UNSUPPORTED_IMAGE_TYPE` | 400 | 지원하지 않는 이미지 형식 |
| `FILE_SIZE_EXCEEDED` | 400 | 업로드 파일 크기 초과 |

### 상품 이미지 삭제

상품 판매자가 상품 이미지를 삭제한다.

- Method: `DELETE`
- Path: `/api/products/{productId}/images/{imageId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |
| `imageId` | Long | 이미지 ID |

#### Response Data

```json
{
  "deleted": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_ACCESS_DENIED` | 403 | 판매자가 아닌 사용자의 삭제 시도 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `IMAGE_NOT_FOUND` | 404 | 이미지 없음 |

## 관리자 API

관리자 API는 운영상 필요한 상품 숨김과 카테고리 관리를 담당한다.
카테고리 관리 API는 [카테고리 API](#카테고리-api)에 정리한다.

### 상품 숨김 상태 변경

관리자가 상품의 숨김 상태를 변경한다. 상품 판매 상태(`status`)는 변경하지 않고 `hidden` 값만 변경한다.

- Method: `PATCH`
- Path: `/api/admin/products/{productId}/hidden`
- 인증: 관리자 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `hidden` | boolean | Y | `true`이면 숨김, `false`이면 숨김 해제 |

```json
{
  "hidden": true
}
```

#### Response Data

```json
{
  "productId": 10,
  "status": "ON_SALE",
  "hidden": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | `hidden` 누락 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `FORBIDDEN` | 403 | 관리자 권한 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |

### 숨긴 상품 조회

관리자가 숨김 처리된 상품 목록을 조회한다.

- Method: `GET`
- Path: `/api/admin/products/hidden`
- 인증: 관리자 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "status": "ON_SALE",
      "hidden": true,
      "sellerNickname": "열무판매자",
      "updatedAt": "2026-06-22T18:40:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `FORBIDDEN` | 403 | 관리자 권한 없음 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

## 검색 API

검색 API는 상품 검색과 인기 검색어 조회를 담당한다.
상품 검색 결과 캐시와 인기 검색어 집계는 `docs/adr/002-redis-search-cache.md`를 따른다.
인기 검색어는 상품 검색 요청 시 키워드를 Redis 캐시에 집계한 값을 기준으로 반환한다.
Redis 캐시 키와 인기 검색어 집계용 자료구조는 관계형 ERD 테이블 계약에 포함하지 않는다.

### 상품 검색

키워드와 조건으로 상품을 검색한다.

- Method: `GET`
- Path: `/api/search/products`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | String | N | 검색 키워드 |
| `minPrice` | int | N | 최소 가격 |
| `maxPrice` | int | N | 최대 가격 |
| `status` | ProductStatus | N | 상품 상태. 기본값 `ON_SALE`. 공개 목록은 `hidden=false`인 상품만 반환하며 `DELETED`는 조회 불가 |
| `categoryId` | Long | N | 카테고리 ID. P1부터 사용 |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `sort` | String | N | 정렬 조건 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "price": 430000,
      "status": "ON_SALE",
      "thumbnailUrl": "https://cdn.example.com/products/10/thumbnail.jpg",
      "sellerNickname": "열무판매자",
      "createdAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### 단계별 필드

P0에서는 `categoryId` 검색 조건을 사용하지 않고, `thumbnailUrl`은 `null`이다.

| 구분 | 필드 | 도입 단계 |
| --- | --- | --- |
| Query | `keyword`, `minPrice`, `maxPrice`, `status`, `page`, `size`, `sort` | P0 |
| Query | `categoryId` | P1 |
| Response | `content[].productId`, `content[].title`, `content[].price`, `content[].status`, `content[].sellerNickname`, `content[].createdAt`, `page`, `size`, `totalElements`, `totalPages`, `hasNext` | P0 |
| Response | `content[].thumbnailUrl` | P1 |

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 가격 범위 오류 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 인기 검색어 조회

사용자가 많이 검색한 키워드 상위 목록을 조회한다.

- Method: `GET`
- Path: `/api/search/popular-keywords`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | int | N | 조회할 키워드 개수. 기본값 `10`, 최대 `50` |

#### Response Data

```json
{
  "keywords": [
    {
      "keyword": "아이패드",
      "rank": 1,
      "searchCount": 124
    }
  ]
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | `limit` 범위 오류 |

## 주문 API

주문 API는 상품 거래 확정, 구매 내역 조회, 취소, 배송 증빙 등록을 담당한다.
P0에서는 주문 생성, 주문 상세/목록 조회, 취소를 사용하고, 배송 증빙과 거래 완료는 P1에서 도입한다.
동시 주문 제어는 `docs/adr/001-concurrent-order-control.md`를 따른다.
주문/결제/배송/환불/분쟁 상태 흐름은 `docs/adr/005-mock-safe-payment-transaction-policy.md`를 따른다.

### 주문 생성

로그인한 구매자가 판매 중인 상품을 주문한다.
주문 성공 시 상품은 예약 중 상태가 된다.

- Method: `POST`
- Path: `/api/products/{productId}/orders`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Body

없음

#### Response Data

```json
{
  "orderId": 100,
  "product": {
    "productId": 10,
    "title": "아이패드 미니 6세대",
    "price": 430000
  },
  "buyer": {
    "userId": 2,
    "nickname": "열무구매자"
  },
  "seller": {
    "userId": 1,
    "nickname": "열무판매자"
  },
  "status": "CREATED",
  "createdAt": "2026-06-22T18:45:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `PRODUCT_NOT_ON_SALE` | 409 | 판매 중이 아닌 상품 |
| `CANNOT_ORDER_OWN_PRODUCT` | 400 | 자신의 상품 주문 시도 |
| `ORDER_ALREADY_EXISTS` | 409 | 동일 상품에 유효한 주문이 이미 존재 |

### 주문 상세 조회

주문 참여자가 주문 상세 정보를 조회한다.

- Method: `GET`
- Path: `/api/orders/{orderId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Response Data

```json
{
  "orderId": 100,
  "product": {
    "productId": 10,
    "title": "아이패드 미니 6세대",
    "price": 430000,
    "status": "RESERVED"
  },
  "buyer": {
    "userId": 2,
    "nickname": "열무구매자"
  },
  "seller": {
    "userId": 1,
    "nickname": "열무판매자"
  },
  "payment": {
    "paymentId": 200,
    "status": "PENDING"
  },
  "status": "CREATED",
  "createdAt": "2026-06-22T18:45:00+09:00",
  "updatedAt": "2026-06-22T18:45:00+09:00"
}
```

#### 단계별 응답 필드

P0 응답에는 `payment`가 포함되지 않는다.

| 필드 | 도입 단계 |
| --- | --- |
| `orderId`, `product`, `buyer`, `seller`, `status`, `createdAt`, `updatedAt` | P0 |
| `payment` | P1 |

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 참여자가 아닌 사용자의 조회 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |

### 내 구매 주문 목록

로그인한 사용자가 구매자로 참여한 주문 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/orders`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `status` | OrderStatus | N | 주문 상태 |

#### Response Data

```json
{
  "content": [
    {
      "orderId": 100,
      "productId": 10,
      "productTitle": "아이패드 미니 6세대",
      "price": 430000,
      "sellerNickname": "열무판매자",
      "status": "CREATED",
      "createdAt": "2026-06-22T18:45:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 내 판매 주문 목록

로그인한 사용자가 판매자로 참여한 주문 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/sales`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `status` | OrderStatus | N | 주문 상태 |

#### Response Data

```json
{
  "content": [
    {
      "orderId": 100,
      "productId": 10,
      "productTitle": "아이패드 미니 6세대",
      "price": 430000,
      "buyerNickname": "열무구매자",
      "status": "CREATED",
      "createdAt": "2026-06-22T18:45:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 주문 취소

주문 구매자가 취소 가능한 주문을 취소한다.
P0에서는 주문 생성 상태의 주문만 취소할 수 있다.

- Method: `POST`
- Path: `/api/orders/{orderId}/cancel`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | String | N | 취소 사유 |

```json
{
  "reason": "구매 의사 취소"
}
```

#### Response Data

```json
{
  "orderId": 100,
  "status": "CANCELED",
  "productStatus": "ON_SALE",
  "canceledAt": "2026-06-22T19:00:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 구매자가 아닌 사용자의 취소 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `INVALID_ORDER_STATUS` | 409 | 취소할 수 없는 주문 상태 |

### 배송 증빙 등록

판매자가 결제 완료된 주문에 배송 증빙을 등록한다.
배송 증빙 등록 후 주문은 배송 중 상태가 되며, 구매자의 단순 주문 취소는 환불 요청 흐름으로 전환된다.

- Method: `PATCH`
- Path: `/api/orders/{orderId}/shipping`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `trackingNumber` | String | Y | 배송 증빙용 송장 번호 |

```json
{
  "trackingNumber": "1234-5678-9012"
}
```

#### Response Data

```json
{
  "orderId": 100,
  "status": "SHIPPING",
  "trackingNumber": "1234-5678-9012",
  "shippedAt": "2026-06-22T19:20:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 송장 번호 누락 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 판매자가 아닌 사용자의 배송 증빙 등록 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `INVALID_ORDER_STATUS` | 409 | 배송 증빙을 등록할 수 없는 주문 상태 |

## 채팅 API

채팅 API는 상품별 구매자와 판매자 사이의 채팅방, 메시지 조회, STOMP 메시징을 담당한다.

### 채팅방 생성

구매자가 상품 판매자와의 채팅방을 생성한다.
동일 상품에 대해 같은 구매자와 판매자 사이의 채팅방이 이미 있으면 기존 채팅방을 반환한다.

- Method: `POST`
- Path: `/api/products/{productId}/chat-rooms`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Body

없음

#### Response Data

```json
{
  "roomId": 300,
  "product": {
    "productId": 10,
    "title": "아이패드 미니 6세대"
  },
  "buyer": {
    "userId": 2,
    "nickname": "열무구매자"
  },
  "seller": {
    "userId": 1,
    "nickname": "열무판매자"
  },
  "createdAt": "2026-06-22T18:50:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `CANNOT_CHAT_OWN_PRODUCT` | 400 | 자신의 상품에 채팅방 생성 시도 |

### 내 채팅방 목록 조회

로그인한 사용자가 참여 중인 채팅방 목록을 조회한다.

- Method: `GET`
- Path: `/api/chat-rooms`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "roomId": 300,
      "productId": 10,
      "productTitle": "아이패드 미니 6세대",
      "opponentNickname": "열무판매자",
      "lastMessage": "거래 가능할까요?",
      "lastMessageAt": "2026-06-22T18:55:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 채팅방 상세 조회

채팅방 참여자가 채팅방 상세 정보를 조회한다.

- Method: `GET`
- Path: `/api/chat-rooms/{roomId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |

#### Response Data

```json
{
  "roomId": 300,
  "product": {
    "productId": 10,
    "title": "아이패드 미니 6세대",
    "status": "ON_SALE"
  },
  "buyer": {
    "userId": 2,
    "nickname": "열무구매자"
  },
  "seller": {
    "userId": 1,
    "nickname": "열무판매자"
  },
  "createdAt": "2026-06-22T18:50:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `CHAT_ROOM_ACCESS_DENIED` | 403 | 채팅방 참여자가 아닌 사용자의 조회 시도 |
| `CHAT_ROOM_NOT_FOUND` | 404 | 채팅방 없음 |

### 이전 메시지 조회

채팅방의 이전 메시지를 조회한다.

- Method: `GET`
- Path: `/api/chat-rooms/{roomId}/messages`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `beforeMessageId` | Long | N | 이 메시지보다 이전 메시지 조회 |
| `size` | int | N | 조회 개수. 기본값 `30` |

#### Response Data

```json
{
  "messages": [
    {
      "messageId": 500,
      "roomId": 300,
      "senderId": 2,
      "senderNickname": "열무구매자",
      "content": "거래 가능할까요?",
      "createdAt": "2026-06-22T18:55:00+09:00"
    }
  ],
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `CHAT_ROOM_ACCESS_DENIED` | 403 | 채팅방 참여자가 아닌 사용자의 조회 시도 |
| `CHAT_ROOM_NOT_FOUND` | 404 | 채팅방 없음 |

### 웹소켓 연결

STOMP over WebSocket 연결을 시작한다.

- Method: `WS Connect`
- Path: `/ws`
- 인증: 필요
- HTTP Status: `101 Switching Protocols`

#### Connect Headers

```http
Authorization: Bearer {accessToken}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_TOKEN` | 401 | 잘못된 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |

### 채팅방 메시지 구독

채팅방 메시지를 구독한다.

- Method: `SUB`
- Destination: `/sub/chat-rooms/{roomId}`
- 인증: 필요

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |

#### Message Payload

```json
{
  "messageId": 500,
  "roomId": 300,
  "senderId": 2,
  "senderNickname": "열무구매자",
  "content": "거래 가능할까요?",
  "createdAt": "2026-06-22T18:55:00+09:00"
}
```

#### Errors

| 코드 | 발생 조건 |
| --- | --- |
| `CHAT_ROOM_ACCESS_DENIED` | 채팅방 참여자가 아닌 사용자의 구독 시도 |
| `CHAT_ROOM_NOT_FOUND` | 채팅방 없음 |

### 메시지 전송

채팅방에 메시지를 전송한다.

- Method: `PUB`
- Destination: `/pub/chat-rooms/{roomId}/message`
- 인증: 필요

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |

#### Message Payload

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 메시지 내용 |

```json
{
  "content": "거래 가능할까요?"
}
```

#### Response Payload

구독 destination `/sub/chat-rooms/{roomId}`로 저장된 메시지가 발행된다.

```json
{
  "messageId": 500,
  "roomId": 300,
  "senderId": 2,
  "senderNickname": "열무구매자",
  "content": "거래 가능할까요?",
  "createdAt": "2026-06-22T18:55:00+09:00"
}
```

#### Errors

| 코드 | 발생 조건 |
| --- | --- |
| `VALIDATION_FAILED` | 메시지 내용 오류 |
| `CHAT_ROOM_ACCESS_DENIED` | 채팅방 참여자가 아닌 사용자의 전송 시도 |
| `CHAT_ROOM_NOT_FOUND` | 채팅방 없음 |
| `MESSAGE_SEND_NOT_ALLOWED` | 메시지를 보낼 수 없는 채팅방 |

## 결제 API

결제 API는 P1 범위의 모의 결제를 담당한다.
실제 결제사 연동은 하지 않고 성공, 실패, 취소를 시뮬레이션한다.
결제 실패와 결제 취소는 상품 예약을 해제해 상품을 다시 판매 중 상태로 되돌린다.
모의 안전결제와 거래 상태 정책은 `docs/adr/005-mock-safe-payment-transaction-policy.md`를 따른다.

### 결제 상태 전이

| 상황 | 결제 상태 | 주문 상태 | 상품 상태 |
| --- | --- | --- | --- |
| 결제 성공 | `PAID` | `PAID` | `RESERVED` |
| 결제 실패 | `FAILED` | `CANCELED` | `ON_SALE` |
| 결제 전 취소 | `CANCELED` | `CANCELED` | `ON_SALE` |
| 배송 전 결제 후 취소 | `REFUNDED` | `REFUNDED` | `ON_SALE` |
| 배송 증빙 등록 | `PAID` | `SHIPPING` | `RESERVED` |
| 구매 확정 | `PAID` | `COMPLETED` | `SOLD_OUT` |

### 결제 요청

주문에 대해 모의 결제를 요청한다.

- Method: `POST`
- Path: `/api/orders/{orderId}/payment`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `Idempotency-Key` | Y | 중복 결제 방지용 멱등키. 새 결제 시도마다 새 값을 만들고, 같은 결제 시도 재요청에는 같은 값을 사용한다 |

```http
Idempotency-Key: 9b2e7c1a-3f4d-4a6b-8e21-0c5f7a9d1234
```

#### 멱등성 규칙

| 상황 | 처리 |
| --- | --- |
| 결제가 없는 주문에 새 `Idempotency-Key`로 요청 | 결제를 생성하고 `201 Created`로 결과를 반환한다 |
| 같은 주문에 같은 `Idempotency-Key`로 재요청 | 결제를 새로 만들지 않고 기존 결제 결과를 `200 OK`로 반환한다 |
| 같은 주문에 다른 `Idempotency-Key`로 재요청 | 다른 결제 시도로 보고 `PAYMENT_ALREADY_EXISTS`를 반환한다 |
| 다른 주문 결제 요청 | 새 결제 시도이므로 새 `Idempotency-Key`를 사용한다 |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `method` | String | Y | 결제 수단. 예: `MOCK_CARD` |
| `result` | String | N | 모의 결제 결과. 예: `PAID`, `FAILED` |

> 결제 금액은 클라이언트가 보내지 않는다. 서버가 주문의 `order_price`로 산정·검증한다.

```json
{
  "method": "MOCK_CARD",
  "result": "PAID"
}
```

#### Response Data

```json
{
  "paymentId": 200,
  "orderId": 100,
  "amount": 430000,
  "method": "MOCK_CARD",
  "status": "PAID",
  "orderStatus": "PAID",
  "productStatus": "RESERVED",
  "paidAt": "2026-06-22T19:10:00+09:00"
}
```

`result`가 `FAILED`이면 주문과 상품 예약을 함께 해제한다.

```json
{
  "paymentId": 200,
  "orderId": 100,
  "amount": 430000,
  "method": "MOCK_CARD",
  "status": "FAILED",
  "orderStatus": "CANCELED",
  "productStatus": "ON_SALE",
  "failedAt": "2026-06-22T19:10:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 결제 수단 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 구매자가 아닌 사용자의 결제 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `PAYMENT_ALREADY_EXISTS` | 409 | 해당 주문의 결제가 이미 존재하고 요청 `Idempotency-Key`가 기존 결제와 다름 |
| `INVALID_ORDER_STATUS` | 409 | 결제할 수 없는 주문 상태 |

### 결제 상태 조회

결제 상태를 조회한다.

- Method: `GET`
- Path: `/api/payments/{paymentId}/status`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 결제 ID |

#### Response Data

```json
{
  "paymentId": 200,
  "orderId": 100,
  "status": "PAID",
  "amount": 430000,
  "paidAt": "2026-06-22T19:10:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PAYMENT_ACCESS_DENIED` | 403 | 결제 당사자가 아닌 사용자의 조회 시도 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |

### 결제 상세 조회

결제 상세 정보를 조회한다.

- Method: `GET`
- Path: `/api/payments/{paymentId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 결제 ID |

#### Response Data

```json
{
  "paymentId": 200,
  "orderId": 100,
  "amount": 430000,
  "method": "MOCK_CARD",
  "status": "PAID",
  "paidAt": "2026-06-22T19:10:00+09:00",
  "canceledAt": null
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PAYMENT_ACCESS_DENIED` | 403 | 결제 당사자가 아닌 사용자의 조회 시도 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |

### 결제 취소

결제를 취소한다.
결제 전(`PENDING`) 취소 시 결제와 주문은 `CANCELED`가 되고 상품은 `ON_SALE`로 돌아간다.
배송 증빙 등록 전 결제 후(`PAID`) 취소 시 결제와 주문은 `REFUNDED`로 종료하며 상품은 `ON_SALE`로 돌아간다.
배송 증빙 등록 후에는 결제 취소가 아니라 `POST /api/orders/{orderId}/refund-requests` 환불 요청 흐름을 사용한다.
모의 결제이므로 실제 환불은 하지 않고 상태만 변경한다.

- Method: `POST`
- Path: `/api/payments/{paymentId}/cancel`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 결제 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | String | N | 취소 사유 |

```json
{
  "reason": "거래 취소"
}
```

취소 사유는 운영 확인용으로 저장할 수 있지만 응답 데이터에는 포함하지 않는다.

#### Response Data

결제 전 취소 예시:

```json
{
  "paymentId": 200,
  "orderId": 100,
  "status": "CANCELED",
  "orderStatus": "CANCELED",
  "productStatus": "ON_SALE",
  "canceledAt": "2026-06-22T19:20:00+09:00"
}
```

배송 전 결제 후 취소 예시:

```json
{
  "paymentId": 200,
  "orderId": 100,
  "status": "REFUNDED",
  "orderStatus": "REFUNDED",
  "productStatus": "ON_SALE",
  "canceledAt": "2026-06-22T19:20:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PAYMENT_ACCESS_DENIED` | 403 | 결제 당사자가 아닌 사용자의 취소 시도 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `INVALID_PAYMENT_STATUS` | 409 | 취소할 수 없는 결제 상태 |

### 구매 확정

구매자가 거래 완료를 확정한다.

- Method: `POST`
- Path: `/api/orders/{orderId}/confirm`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Request Body

없음

#### Response Data

```json
{
  "orderId": 100,
  "status": "COMPLETED",
  "productStatus": "SOLD_OUT",
  "confirmedAt": "2026-06-22T19:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 구매자가 아닌 사용자의 확정 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `INVALID_ORDER_STATUS` | 409 | 구매 확정할 수 없는 주문 상태 |

## 카테고리 API

카테고리 API는 P1 범위의 카테고리 조회와 관리자 관리를 담당한다.

### 카테고리 목록 조회

상품 등록과 탐색에 사용할 카테고리 목록을 조회한다.

- Method: `GET`
- Path: `/api/categories`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Request Body

없음

#### Response Data

```json
{
  "categories": [
    {
      "categoryId": 3,
      "name": "디지털기기"
    }
  ]
}
```

#### Errors

없음

### 카테고리 생성

관리자가 카테고리를 생성한다.

- Method: `POST`
- Path: `/api/admin/categories`
- 인증: 관리자 필요
- HTTP Status: `201 Created`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 카테고리명 |

```json
{
  "name": "디지털기기"
}
```

#### Response Data

```json
{
  "categoryId": 3,
  "name": "디지털기기",
  "createdAt": "2026-06-22T18:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 카테고리명 누락 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `FORBIDDEN` | 403 | 관리자 권한 없음 |
| `CATEGORY_NAME_ALREADY_EXISTS` | 409 | 이미 존재하는 카테고리명 |

### 카테고리 수정

관리자가 카테고리 정보를 수정한다.

- Method: `PUT`
- Path: `/api/admin/categories/{categoryId}`
- 인증: 관리자 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `categoryId` | Long | 카테고리 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 카테고리명 |

```json
{
  "name": "디지털/가전"
}
```

#### Response Data

```json
{
  "categoryId": 3,
  "name": "디지털/가전",
  "updatedAt": "2026-06-22T18:40:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 카테고리명 누락 또는 값 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `FORBIDDEN` | 403 | 관리자 권한 없음 |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리 없음 |
| `CATEGORY_NAME_ALREADY_EXISTS` | 409 | 이미 존재하는 카테고리명 |

### 카테고리 삭제

관리자가 카테고리를 삭제한다.

- Method: `DELETE`
- Path: `/api/admin/categories/{categoryId}`
- 인증: 관리자 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `categoryId` | Long | 카테고리 ID |

#### Response Data

```json
{
  "deleted": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `FORBIDDEN` | 403 | 관리자 권한 없음 |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리 없음 |
| `CATEGORY_IN_USE` | 409 | 상품이 연결된 카테고리 삭제 시도 |

### 카테고리별 상품 조회

특정 카테고리에 속한 상품 목록을 조회한다.
숨김·삭제 상품은 반환하지 않으며, 공개 목록은 `hidden=false`이고 `status != DELETED`인 상품만 반환한다.

- Method: `GET`
- Path: `/api/categories/{categoryId}/products`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `categoryId` | Long | 카테고리 ID |

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |
| `sort` | String | N | 정렬 조건 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "price": 430000,
      "status": "ON_SALE",
      "thumbnailUrl": "https://cdn.example.com/products/10/thumbnail.jpg",
      "sellerNickname": "열무판매자",
      "createdAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리 없음 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

## 찜 API

찜 API는 P1 범위의 관심 상품 저장 기능을 담당한다.

### 찜하기

로그인한 사용자가 상품을 찜한다.

- Method: `POST`
- Path: `/api/products/{productId}/wishes`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Request Body

없음

#### Response Data

```json
{
  "productId": 10,
  "wished": true,
  "wishCount": 13
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `WISH_ALREADY_EXISTS` | 409 | 이미 찜한 상품 |

### 찜 취소

로그인한 사용자가 상품 찜을 취소한다.

- Method: `DELETE`
- Path: `/api/products/{productId}/wishes`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Response Data

```json
{
  "productId": 10,
  "wished": false,
  "wishCount": 12
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `WISH_NOT_FOUND` | 404 | 찜 없음 |

### 내가 찜한 상품 목록

로그인한 사용자가 찜한 상품 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/wishes`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "title": "아이패드 미니 6세대",
      "price": 430000,
      "status": "ON_SALE",
      "thumbnailUrl": "https://cdn.example.com/products/10/thumbnail.jpg",
      "wishedAt": "2026-06-22T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 찜 여부

로그인한 사용자가 특정 상품을 찜했는지 조회한다.

- Method: `GET`
- Path: `/api/products/{productId}/wish-status`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Response Data

```json
{
  "productId": 10,
  "wished": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |

### 상품 찜 수 조회

상품의 찜 수를 조회한다.

- Method: `GET`
- Path: `/api/products/{productId}/wishes/count`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

#### Response Data

```json
{
  "productId": 10,
  "wishCount": 12
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |

## 리뷰 API

리뷰 API는 P2 범위의 거래 리뷰와 유저 평점을 담당한다.
거래 완료 후 주문 참여자가 상대방에게 각각 한 번씩 리뷰할 수 있는 정책은 `docs/adr/005-mock-safe-payment-transaction-policy.md`를 따른다.

### 유저 리뷰 작성

거래가 완료된 주문의 참여자가 같은 주문의 상대방에게 리뷰를 작성한다.

- Method: `POST`
- Path: `/api/orders/{orderId}/reviews`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `score` | int | Y | 평점. 1부터 5까지 |
| `content` | String | Y | 리뷰 내용 |

```json
{
  "score": 5,
  "content": "시간 약속을 잘 지켜주셨어요."
}
```

#### Response Data

```json
{
  "reviewId": 700,
  "orderId": 100,
  "reviewerId": 2,
  "revieweeId": 1,
  "score": 5,
  "content": "시간 약속을 잘 지켜주셨어요.",
  "createdAt": "2026-06-22T20:00:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 평점 범위 또는 내용 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 참여자가 아닌 사용자의 리뷰 작성 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `REVIEW_NOT_ALLOWED` | 409 | 거래 완료 전 리뷰 작성 시도 |
| `REVIEW_ALREADY_EXISTS` | 409 | 해당 주문 상대방에게 이미 작성한 리뷰 |

### 유저 리뷰 수정

리뷰 작성자가 자신의 리뷰를 수정한다.

- Method: `PATCH`
- Path: `/api/reviews/{reviewId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `reviewId` | Long | 리뷰 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `score` | int | N | 평점. 1부터 5까지 |
| `content` | String | N | 리뷰 내용 |

```json
{
  "score": 4,
  "content": "거래는 좋았고 응답은 조금 늦었습니다."
}
```

#### Response Data

```json
{
  "reviewId": 700,
  "score": 4,
  "content": "거래는 좋았고 응답은 조금 늦었습니다.",
  "updatedAt": "2026-06-22T20:10:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 수정할 값이 없거나 값 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `REVIEW_ACCESS_DENIED` | 403 | 작성자가 아닌 사용자의 수정 시도 |
| `REVIEW_NOT_FOUND` | 404 | 리뷰 없음 |

### 유저 리뷰 삭제

리뷰 작성자가 자신의 리뷰를 삭제한다.

- Method: `DELETE`
- Path: `/api/reviews/{reviewId}`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `reviewId` | Long | 리뷰 ID |

#### Response Data

```json
{
  "deleted": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `REVIEW_ACCESS_DENIED` | 403 | 작성자가 아닌 사용자의 삭제 시도 |
| `REVIEW_NOT_FOUND` | 404 | 리뷰 없음 |

### 내가 작성한 리뷰

로그인한 사용자가 작성한 리뷰 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/reviews/written`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "reviewId": 700,
      "orderId": 100,
      "revieweeNickname": "열무판매자",
      "score": 5,
      "content": "시간 약속을 잘 지켜주셨어요.",
      "createdAt": "2026-06-22T20:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 내가 받은 리뷰

로그인한 사용자가 받은 리뷰 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/me/reviews/received`
- 인증: 필요
- HTTP Status: `200 OK`

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "reviewId": 700,
      "orderId": 100,
      "reviewerNickname": "열무구매자",
      "score": 5,
      "content": "시간 약속을 잘 지켜주셨어요.",
      "createdAt": "2026-06-22T20:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 특정 유저가 받은 리뷰 목록

특정 유저가 받은 공개 리뷰 목록을 조회한다.

- Method: `GET`
- Path: `/api/users/{userId}/reviews`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | Long | 리뷰 대상 유저 ID |

#### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | int | N | 페이지 번호 |
| `size` | int | N | 페이지 크기 |

#### Response Data

```json
{
  "content": [
    {
      "reviewId": 700,
      "reviewerNickname": "열무구매자",
      "score": 5,
      "content": "시간 약속을 잘 지켜주셨어요.",
      "createdAt": "2026-06-22T20:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `USER_NOT_FOUND` | 404 | 회원 없음 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

### 특정 유저의 평점 조회

특정 유저의 평균 평점과 리뷰 수를 조회한다.

- Method: `GET`
- Path: `/api/users/{userId}/rating`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | Long | 평점 조회 대상 유저 ID |

#### Response Data

```json
{
  "userId": 1,
  "nickname": "열무판매자",
  "averageRating": 4.8,
  "reviewCount": 25
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `USER_NOT_FOUND` | 404 | 회원 없음 |

## 환불/분쟁 API

환불/분쟁 API는 P2 범위의 배송 후 환불 요청과 분쟁 종료를 담당한다.
배송 증빙 등록 후 구매자가 거래를 취소하려면 단순 주문 취소가 아니라 환불 요청을 생성한다.
환불 요청과 분쟁 상태 흐름은 `docs/adr/005-mock-safe-payment-transaction-policy.md`를 따른다.

### 환불 요청 거절

판매자가 환불 요청을 거절하고 주문을 분쟁 상태로 전환한다.

- Method: `POST`
- Path: `/api/refund-requests/{refundRequestId}/reject`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `refundRequestId` | Long | 환불 요청 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | String | N | 거절 사유 |

```json
{
  "reason": "상품 상태 설명과 다르지 않습니다."
}
```

#### Response Data

```json
{
  "refundRequestId": 300,
  "orderId": 100,
  "status": "DISPUTED",
  "orderStatus": "DISPUTED",
  "rejectedAt": "2026-06-22T21:10:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `REFUND_REQUEST_ACCESS_DENIED` | 403 | 주문 판매자가 아닌 사용자의 거절 시도 |
| `REFUND_REQUEST_NOT_FOUND` | 404 | 환불 요청 없음 |
| `INVALID_REFUND_REQUEST_STATUS` | 409 | 거절할 수 없는 환불 요청 상태 |

### 환불요청

구매자가 배송 중인 주문에 환불 요청을 생성한다.

- Method: `POST`
- Path: `/api/orders/{orderId}/refund-requests`
- 인증: 필요
- HTTP Status: `201 Created`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | String | Y | 환불 요청 사유 |

```json
{
  "reason": "상품에 설명과 다른 하자가 있습니다."
}
```

#### Response Data

```json
{
  "refundRequestId": 300,
  "orderId": 100,
  "status": "REQUESTED",
  "orderStatus": "REFUND_REQUESTED",
  "requestedAt": "2026-06-22T21:00:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 환불 요청 사유 누락 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `ORDER_ACCESS_DENIED` | 403 | 주문 구매자가 아닌 사용자의 환불 요청 시도 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `REFUND_REQUEST_ALREADY_EXISTS` | 409 | 해당 주문의 환불 요청이 이미 존재 |
| `INVALID_ORDER_STATUS` | 409 | 환불 요청을 생성할 수 없는 주문 상태 |

### 환불 요청 승인

판매자가 환불 요청을 승인하고 주문을 환불 상태로 전환한다.
환불 요청이 승인되면 상품은 별도 반품 확인 없이 자동으로 판매 중 상태가 된다.

- Method: `POST`
- Path: `/api/refund-requests/{refundRequestId}/approve`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `refundRequestId` | Long | 환불 요청 ID |

#### Request Body

없음

#### Response Data

```json
{
  "refundRequestId": 300,
  "orderId": 100,
  "status": "APPROVED",
  "orderStatus": "REFUNDED",
  "productStatus": "ON_SALE",
  "approvedAt": "2026-06-22T21:15:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `REFUND_REQUEST_ACCESS_DENIED` | 403 | 주문 판매자가 아닌 사용자의 승인 시도 |
| `REFUND_REQUEST_NOT_FOUND` | 404 | 환불 요청 없음 |
| `INVALID_REFUND_REQUEST_STATUS` | 409 | 승인할 수 없는 환불 요청 상태 |

### 분쟁 종료

분쟁 상태의 환불 요청을 구매자 환불 또는 거래 완료 방향으로 종료한다.
구매자 환불 방향으로 종료하면 상품은 별도 반품 확인 없이 자동으로 판매 중 상태가 된다.

- Method: `POST`
- Path: `/api/refund-requests/{refundRequestId}/resolve`
- 인증: 필요
- HTTP Status: `200 OK`

#### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `refundRequestId` | Long | 환불 요청 ID |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `resolution` | String | Y | `REFUND` 또는 `COMPLETE` |
| `reason` | String | N | 종료 사유 |

```json
{
  "resolution": "REFUND",
  "reason": "구매자 환불로 종료"
}
```

#### Response Data

```json
{
  "refundRequestId": 300,
  "orderId": 100,
  "status": "CLOSED",
  "orderStatus": "REFUNDED",
  "productStatus": "ON_SALE",
  "resolvedAt": "2026-06-22T21:30:00+09:00"
}
```

`resolution`이 `COMPLETE`이면 주문은 거래 완료 상태가 되고 상품은 판매 완료 상태가 된다.

```json
{
  "refundRequestId": 300,
  "orderId": 100,
  "status": "CLOSED",
  "orderStatus": "COMPLETED",
  "productStatus": "SOLD_OUT",
  "resolvedAt": "2026-06-22T21:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 종료 방향 값 오류 |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `REFUND_REQUEST_ACCESS_DENIED` | 403 | 분쟁 종료 권한 없음 |
| `REFUND_REQUEST_NOT_FOUND` | 404 | 환불 요청 없음 |
| `INVALID_REFUND_REQUEST_STATUS` | 409 | 종료할 수 없는 환불 요청 상태 |
