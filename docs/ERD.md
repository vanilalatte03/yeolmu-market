# ERD

열무마켓의 ERD를 이미지 기준으로 정리한 문서입니다.

카테고리, 찜, 결제, 리뷰는 PRD 기준 P1/P2 범위까지 포함합니다. 상태값은 PRD의 상품/주문 상태와 중고거래 결제 흐름을 기준으로 정의했습니다.

검색 성능 개선, 인기 검색어 집계, 로그아웃 토큰 블랙리스트는 Redis를 기준으로 관리하므로 이 관계형 ERD에는 포함하지 않습니다.
Redis 검색 캐시 결정은 `docs/adr/002-redis-search-cache.md`, JWT 로그아웃 블랙리스트 결정은 `docs/adr/004-jwt-logout-blacklist.md`를 따릅니다.

## 관계도

```mermaid
erDiagram
    category ||--o{ product : categorizes
    users ||--o{ product : sells
    product ||--o{ product_image : has
    users ||--o{ wish : creates
    product ||--o{ wish : wished_by

    product ||--o{ chatroom : discussed_in
    users ||--o{ chatroom : sells_in
    users ||--o{ chatroom : buys_in
    chatroom ||--o{ chatmessage : contains
    users ||--o{ chatmessage : sends

    users ||--o{ orders : buys
    users ||--o{ orders : sells
    product ||--o{ orders : ordered_as
    orders ||--o| payment : paid_by
    orders ||--o| review : reviewed_by
    users ||--o{ review : writes
    users ||--o{ review : receives

    category {
        BIGINT id PK "카테고리 ID"
        VARCHAR name "카테고리명"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
    }

    product {
        BIGINT id PK "상품 ID"
        BIGINT category_id FK "카테고리 ID, nullable"
        BIGINT seller_id FK "판매자 ID"
        VARCHAR title "상품명"
        TEXT description "상품 설명"
        INT price "상품 가격"
        VARCHAR status "상품 상태"
        VARCHAR visibility "노출 상태"
        INT version "낙관적 락 버전"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
        DATETIME deleted_at "삭제일, nullable"
    }

    product_image {
        BIGINT id PK "이미지 ID"
        BIGINT product_id FK "상품 ID"
        TEXT image_url "이미지 URL"
        BOOLEAN is_thumbnail "대표 이미지 여부"
        DATETIME created_at "생성일"
    }

    users {
        BIGINT id PK "유저 ID"
        VARCHAR nickname "닉네임"
        VARCHAR email "이메일"
        VARCHAR password "비밀번호"
        VARCHAR role "역할"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
    }

    wish {
        BIGINT id PK "찜 ID"
        BIGINT user_id FK "유저 ID"
        BIGINT product_id FK "상품 ID"
    }

    chatroom {
        BIGINT id PK "채팅방 ID"
        BIGINT product_id FK "상품 ID"
        BIGINT seller_id FK "판매자 ID"
        BIGINT buyer_id FK "구매자 ID"
        DATETIME created_at "생성일"
        DATETIME last_message_at "마지막 대화 시점, nullable"
    }

    chatmessage {
        BIGINT id PK "메시지 ID"
        BIGINT chatroom_id FK "채팅방 ID"
        BIGINT sender_id FK "발신자 ID"
        TEXT content "대화내용"
        DATETIME created_at "생성일"
    }

    orders {
        BIGINT id PK "주문 ID"
        BIGINT buyer_id FK "구매자 ID"
        BIGINT seller_id FK "판매자 ID"
        BIGINT product_id FK "상품 ID"
        VARCHAR order_status "주문 상태"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
        INT order_price "주문 가격"
    }

    payment {
        BIGINT id PK "결제 ID"
        BIGINT order_id FK "주문 ID"
        VARCHAR method "결제 수단"
        VARCHAR status "결제 상태"
        INT amount "결제 금액"
        VARCHAR idempotency_key "멱등키"
        DATETIME paid_at "결제일, nullable"
        DATETIME canceled_at "취소일, nullable"
        VARCHAR cancel_reason "취소 사유, nullable"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
    }

    review {
        BIGINT id PK "리뷰 ID"
        BIGINT reviewer_id FK "리뷰어 ID"
        BIGINT seller_id FK "판매자 ID"
        BIGINT order_id FK "주문 ID"
        INT rating "점수"
        VARCHAR content "리뷰 내용"
        DATETIME created_at "생성일"
        DATETIME modified_at "수정일"
    }
```

## 테이블 정의

### category

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 카테고리 ID | id | BIGINT | NOT NULL | PK |
| 카테고리명 | name | VARCHAR(20) | NOT NULL | UNIQUE |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |

### product

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 상품 ID | id | BIGINT | NOT NULL | PK |
| 카테고리 ID | category_id | BIGINT | NULL | FK: category.id. 카테고리는 P1 범위라 미분류 상품 허용 |
| 판매자 ID | seller_id | BIGINT | NOT NULL | FK: users.id |
| 상품명 | title | VARCHAR(100) | NOT NULL | |
| 상품 설명 | description | TEXT | NOT NULL | |
| 상품 가격 | price | INT | NOT NULL | 0보다 커야 함 |
| 상품 상태 | status | VARCHAR(20) | NOT NULL | ON_SALE, RESERVED, SOLD_OUT, DELETED |
| 노출 상태 | visibility | VARCHAR(20) | NOT NULL | VISIBLE, HIDDEN |
| 버전 | version | INT | NOT NULL | JPA 낙관적 락(@Version). 동시 주문 충돌 감지 |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |
| 삭제일 | deleted_at | DATETIME | NULL | 소프트 삭제 시각 |

### product_image

상품 이미지는 P1 범위입니다. 한 상품에 여러 이미지를 등록할 수 있고, 대표 이미지는 `is_thumbnail`로 표시합니다.

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 이미지 ID | id | BIGINT | NOT NULL | PK |
| 상품 ID | product_id | BIGINT | NOT NULL | FK: product.id |
| 이미지 URL | image_url | TEXT | NOT NULL | |
| 대표 이미지 여부 | is_thumbnail | BOOLEAN | NOT NULL | 기본값 false |
| 생성일 | created_at | DATETIME | NOT NULL | 업로드 시각 |

### users

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 유저 ID | id | BIGINT | NOT NULL | PK |
| 닉네임 | nickname | VARCHAR(30) | NOT NULL | |
| 이메일 | email | VARCHAR(255) | NOT NULL | UNIQUE |
| 비밀번호 | password | VARCHAR(255) | NOT NULL | 해시 저장 |
| 역할 | role | VARCHAR(10) | NOT NULL | USER, ADMIN |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |

### wish

제약 조건:

- `UNIQUE (user_id, product_id)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 찜 ID | id | BIGINT | NOT NULL | PK |
| 유저 ID | user_id | BIGINT | NOT NULL | FK: users.id |
| 상품 ID | product_id | BIGINT | NOT NULL | FK: product.id |

같은 사용자는 같은 상품을 한 번만 찜할 수 있습니다.

### chatroom

제약 조건:

- `UNIQUE (product_id, buyer_id, seller_id)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 채팅방 ID | id | BIGINT | NOT NULL | PK |
| 상품 ID | product_id | BIGINT | NOT NULL | FK: product.id |
| 판매자 ID | seller_id | BIGINT | NOT NULL | FK: users.id |
| 구매자 ID | buyer_id | BIGINT | NOT NULL | FK: users.id |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 마지막 대화 시점 | last_message_at | DATETIME | NULL | 메시지 생성 시 갱신 |

동일 상품에 대해 같은 구매자와 판매자 사이의 채팅방은 하나만 유지합니다.

### chatmessage

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 메시지 ID | id | BIGINT | NOT NULL | PK |
| 채팅방 ID | chatroom_id | BIGINT | NOT NULL | FK: chatroom.id |
| 발신자 ID | sender_id | BIGINT | NOT NULL | FK: users.id. 채팅방 참여자만 허용 |
| 대화내용 | content | TEXT | NOT NULL | 빈 메시지 불가 |
| 생성일 | created_at | DATETIME | NOT NULL | |

### orders

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 주문 ID | id | BIGINT | NOT NULL | PK |
| 구매자 ID | buyer_id | BIGINT | NOT NULL | FK: users.id |
| 판매자 ID | seller_id | BIGINT | NOT NULL | FK: users.id |
| 상품 ID | product_id | BIGINT | NOT NULL | FK: product.id |
| 주문 상태 | order_status | VARCHAR(20) | NOT NULL | CREATED, PAID, COMPLETED, CANCELED, REFUNDED |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |
| 주문 가격 | order_price | INT | NOT NULL | 주문 생성 시점의 상품 가격 스냅샷 |

주문이 성공하면 상품은 `RESERVED`가 됩니다. 동일 상품에는 동시에 여러 주문 시도가 가능하지만 성공 주문은 하나만 허용합니다.

### payment

제약 조건:

- `UNIQUE (order_id)`
- `UNIQUE (idempotency_key)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 결제 ID | id | BIGINT | NOT NULL | PK |
| 주문 ID | order_id | BIGINT | NOT NULL | FK: orders.id, UNIQUE |
| 결제 수단 | method | VARCHAR(30) | NOT NULL | 예: MOCK_CARD |
| 결제 상태 | status | VARCHAR(20) | NOT NULL | PENDING, PAID, FAILED, CANCELED, REFUNDED |
| 결제 금액 | amount | INT | NOT NULL | 주문 가격과 일치해야 함 |
| 멱등키 | idempotency_key | VARCHAR(100) | NOT NULL | UNIQUE. 중복 결제 방지 |
| 결제일 | paid_at | DATETIME | NULL | 결제 성공 시각 |
| 취소일 | canceled_at | DATETIME | NULL | 결제 취소 또는 환불 처리 시각 |
| 취소 사유 | cancel_reason | VARCHAR(255) | NULL | 결제 취소 요청 사유. API 응답에는 노출하지 않음 |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |

하나의 주문에는 결제 row를 최대 1건만 연결합니다. P1 모의 결제에서 성공, 실패, 취소를 시뮬레이션합니다.

### review

제약 조건:

- `UNIQUE (order_id)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 리뷰 ID | id | BIGINT | NOT NULL | PK |
| 리뷰어 ID | reviewer_id | BIGINT | NOT NULL | FK: users.id. 구매자 |
| 판매자 ID | seller_id | BIGINT | NOT NULL | FK: users.id. 리뷰 대상 |
| 주문 ID | order_id | BIGINT | NOT NULL | FK: orders.id, UNIQUE |
| 점수 | rating | INT | NOT NULL | 1~5 |
| 리뷰 내용 | content | VARCHAR(255) | NOT NULL | |
| 생성일 | created_at | DATETIME | NOT NULL | |
| 수정일 | modified_at | DATETIME | NOT NULL | |

거래가 완료된 주문의 구매자만 판매자에게 리뷰를 작성할 수 있습니다.

## 상태값 정의

### 상품 상태

| 상태 | 의미 |
| --- | --- |
| ON_SALE | 판매 중. 주문 가능한 상태 |
| RESERVED | 예약 중. 주문이 생성되어 다른 사용자가 주문할 수 없는 상태 |
| SOLD_OUT | 판매 완료. 거래가 완료된 상태 |
| DELETED | 삭제. 판매자가 삭제한 상태 |

### 상품 노출 상태

| 상태 | 의미 |
| --- | --- |
| VISIBLE | 일반 사용자에게 노출되는 상태 |
| HIDDEN | 관리자가 일반 노출을 막은 상태 |

### 주문 상태

| 상태 | 의미 |
| --- | --- |
| CREATED | 주문이 생성되고 상품이 예약된 상태 |
| PAID | 결제가 완료된 상태 |
| COMPLETED | 구매확정 또는 거래 완료 상태 |
| CANCELED | 결제 전 주문이 취소된 상태 |
| REFUNDED | 결제 후 거래가 취소되어 환불된 상태 |

P0에서는 `CREATED`, `CANCELED`만 사용합니다. `PAID`, `COMPLETED`, `REFUNDED`는 결제(P1)와 구매확정 이후 범위에서 사용합니다.

### 결제 상태

| 상태 | 의미 |
| --- | --- |
| PENDING | 결제 요청이 생성되었지만 아직 성공/실패가 확정되지 않은 상태 |
| PAID | 결제가 성공한 상태 |
| FAILED | 결제가 실패한 상태 |
| CANCELED | 결제 전 또는 결제 승인 전 취소된 상태 |
| REFUNDED | 결제 후 취소로 환불된 상태 |

## 관계 요약

| 관계 | 설명 |
| --- | --- |
| category - product | 카테고리는 여러 상품을 분류할 수 있다. 상품의 카테고리는 P1 전까지 비어 있을 수 있다. |
| user - product | 유저는 여러 상품을 판매자로 등록할 수 있다. |
| product - product_image | 상품은 여러 이미지를 가진다. 대표 이미지는 is_thumbnail로 표시한다. |
| user - wish | 유저는 여러 상품을 찜할 수 있다. |
| product - wish | 상품은 여러 유저에게 찜될 수 있다. |
| product - chatroom | 상품은 여러 구매 희망자와의 채팅방을 가질 수 있다. |
| user - chatroom | 유저는 판매자 또는 구매자로 채팅방에 참여한다. |
| chatroom - chatmessage | 채팅방은 여러 메시지를 가진다. |
| user - chatmessage | 유저는 채팅 메시지를 보낼 수 있다. |
| user - order | 유저는 구매자 또는 판매자로 주문에 참여한다. |
| product - order | 상품은 주문으로 거래된다. 성공 주문은 상품별로 하나만 허용한다. |
| order - payment | 주문은 최대 하나의 결제와 연결된다. |
| order - review | 주문은 최대 하나의 리뷰와 연결된다. |
| user - review | 구매자는 리뷰를 작성하고 판매자는 리뷰를 받는다. |
