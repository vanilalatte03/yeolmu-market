# 열무마켓 (yeolmu-market)

중고 상품을 등록·검색하고, 판매자와 실시간 채팅으로 거래를 협의한 뒤 주문으로 확정하는 중고거래 서비스다.

## 핵심 기능

- **상품 등록·검색** — 상품을 등록하고 키워드·가격·상태로 검색해 거래 후보를 찾는다. 반복 검색은 Redis 캐시로 성능을 개선한다.
- **실시간 채팅** — 구매자와 판매자가 WebSocket(STOMP)으로 실시간 메시지를 주고받고, 메시지는 저장되어 다시 조회할 수 있다.
- **동시 주문 제어** — 동일 상품에 여러 구매자가 동시에 주문해도 단 하나의 거래만 확정된다.

## 문서

- [PRD](docs/PRD.md) — 제품 범위와 우선순위
- [API](docs/API.md) — API 계약
- [ERD](docs/ERD.md) — 데이터 모델
- [DEVELOPMENT](docs/DEVELOPMENT.md) — 개발 환경과 실행·검증 명령
- [DEMO_SEED](docs/DEMO_SEED.md) — 프론트 데모 카탈로그 데이터 적재
- [PERFORMANCE_TESTING](docs/PERFORMANCE_TESTING.md) — 성능 테스트 데이터와 k6 수동 검증
- [CONVENTIONS](docs/CONVENTIONS.md) — 코드 컨벤션
- [ADR](docs/adr/README.md) — 기술 결정 기록

## 프로젝트 구조

```text
yeolmu-market/
|-- src/
|   |-- main/
|   |   |-- java/com/guingujig/yeolmumarket/
|   |   |   |-- domain/       # 기능 도메인 모듈
|   |   |   `-- global/       # 공통 설정, 보안, 예외, 응답, 락
|   |   `-- resources/
|   |       |-- db/migration/ # Flyway migration
|   |       |-- static/       # 로컬 데모용 정적 프론트엔드
|   |       `-- application.yml
|   `-- test/
|       |-- java/com/guingujig/yeolmumarket/
|       `-- resources/
|-- docs/                   # 제품, API, ERD, 개발, ADR 문서
|-- http/                   # 수동 API 검증 시나리오
|-- k6/                     # 성능 테스트 스크립트
|-- k8s/                    # Kubernetes 배포 매니페스트
|-- scripts/                # 스킬 동기화/검증 스크립트
|-- docker-compose.yml      # 로컬 MySQL, Redis 인프라
|-- Dockerfile              # 애플리케이션 컨테이너 이미지
`-- build.gradle            # Java 21 / Spring Boot 4.1.0 빌드 설정
```

### 주요 패키지

| 패키지 | 역할 |
| --- | --- |
| `domain/auth` | 회원가입, 로그인, 로그아웃, JWT refresh/revoke |
| `domain/user` | 사용자 조회와 프로필 수정 |
| `domain/product` | 상품 등록·수정·삭제, 이미지, 판매자 상품 관리 |
| `domain/category` | 카테고리 조회와 관리자 카테고리 관리 |
| `domain/search` | 상품 검색, 검색 캐시, 인기 검색어 |
| `domain/wish` | 관심 상품 등록·해제와 집계 |
| `domain/chat` | 채팅방, 메시지, WebSocket/STOMP 처리 |
| `domain/order` | 주문 생성, 확정, 취소, 배송 정보 |
| `domain/payment` | 모의 결제 처리와 결제 취소 |
| `domain/refund` | 환불 요청, 승인, 거절, 분쟁 처리 |
| `domain/review` | 거래 리뷰 작성·수정·삭제와 평점 조회 |
| `global/config` | Spring, Cache, WebSocket, Async, 시간 설정 |
| `global/security` | Spring Security와 JWT 인증 필터 |
| `global/exception` | 공통 예외와 에러 코드 |
| `global/response` | 공통 API 응답과 페이지 응답 |
| `global/lock` | Redis/Redisson 기반 분산 락 |

## 개발

[AGENTS.md](AGENTS.md)는 에이전트 작업 라우터다. 빌드·실행·테스트 명령과 필수 운영 게이트는 [DEVELOPMENT](docs/DEVELOPMENT.md)를 보고, 세부 정본은 `docs/*` 문서를 따른다.

### 환경 셋업

로컬 인프라와 검증 명령은 [DEVELOPMENT](docs/DEVELOPMENT.md)를 따른다.

### 스킬

- 원본은 `.agents/skills/<이름>/SKILL.md`, Claude Code용 복사본은 `.claude/skills/<이름>/SKILL.md`에 둔다.
- 복사·검증은 `scripts/sync-claude-skills.ps1`, `scripts/check-skill-sync.ps1`, CI가 담당한다.
- 현재 공유 스킬은 `review`, `pr-writer`. 세부 트리거·출력 형식은 각 `SKILL.md`를 따른다.

## 트러블 슈팅 문서

- [LOCKING_STRATEGY](docs/troubleshooting/LOCKING_STRATEGY.md) — 비관적/낙관적/분산 락 비교와 주문 상태 변경 동시성 제어 선택 근거
- [SEARCH CACHE STRATEGY](docs/troubleshooting/SEARCH_CACHE.md) — Redis 기반 상품 검색 캐시 전략과 키 설계, 무효화 정책 선택 근거

## DB 인덱스

인덱스 DDL의 정본은 Flyway migration이다. 아래는 현재 코드의 조회·제약 경로에 맞춰 적용된 인덱스를 단독 DDL 형태로 정리한 요약이다.

| Migration | 대상 | 목적 |
| --- | --- | --- |
| `V5__create_product_image.sql` | `product_image` | 상품 이미지 목록을 `product_id`, `created_at`, `id` 순으로 조회하고, 상품당 대표 이미지를 1개로 제한한다. |
| `V7__add_chat_message_async_tracking.sql` | `chatmessage` | 비동기 채팅 메시지의 접수 ID 중복 저장을 막고, 방별 메시지 커서 조회를 최적화한다. |
| `V8__add_product_public_list_index.sql` | `product` | 공개 상품 목록·검색의 상태/숨김/삭제 필터와 최신순 페이지 조회를 최적화한다. |

```sql
-- src/main/resources/db/migration/V5__create_product_image.sql
CREATE UNIQUE INDEX uk_product_image_thumbnail_product
    ON product_image (thumbnail_product_id);

CREATE INDEX idx_product_image_product_created
    ON product_image (product_id, created_at, id);

-- src/main/resources/db/migration/V7__add_chat_message_async_tracking.sql
CREATE UNIQUE INDEX uk_chatmessage_accepted_message_id
    ON chatmessage (accepted_message_id);

CREATE INDEX idx_chatmessage_room_created_id
    ON chatmessage (chatroom_id, created_at, id);

-- src/main/resources/db/migration/V8__add_product_public_list_index.sql
CREATE INDEX idx_product_public_list_latest
    ON product (status, hidden, deleted_at, created_at DESC, id DESC);
```

### 인덱스 적용 전/후 쿼리 성능 비교 및 분석

아래는 동일 상품 목록·검색 쿼리에 대한 MySQL `EXPLAIN` 결과다. 실제 응답 시간 측정값이 아니라 실행 계획 기반의 예상 스캔량과 접근 방식 비교다.

#### 인덱스 적용 전 (`search_no_index.json`)

| table alias | 접근 타입 | possible keys | 사용 key | 예상 rows | filtered | Extra |
| --- | --- | --- | --- | ---: | ---: | --- |
| `p` (`product`) | `ALL` | `FKnuvtfgcf3ohskgoyi6v1eh1jr` | 없음 | `49,525` | `1.25` | `Using where; Using filesort` |
| `u` (`users`) | `eq_ref` | `PRIMARY` | `PRIMARY` | `1` | `100` | 없음 |
| `pi` (`product_image`) | `ref` | `FK6oo0cvcdtb6qmwsga468uuukk` | `FK6oo0cvcdtb6qmwsga468uuukk` | `1` | `100` | `Using where` |

`product` 테이블은 전체 스캔(`ALL`)으로 접근하고, 공개 상품 필터링 후 최신순 정렬을 위해 `Using filesort`가 발생한다.

#### 인덱스 적용 후 (`search_index.json`)

| table alias | 접근 타입 | possible keys | 사용 key | 예상 rows | filtered | Extra |
| --- | --- | --- | --- | ---: | ---: | --- |
| `p` (`product`) | `ref` | `fk_product_seller`, `idx_product_public_list_latest` | `idx_product_public_list_latest` | `24,737` | `100` | `Using index condition` |
| `u` (`users`) | `eq_ref` | `PRIMARY` | `PRIMARY` | `1` | `100` | 없음 |
| `pi` (`product_image`) | `ref` | `idx_product_image_product_created` | `idx_product_image_product_created` | `1` | `100` | `Using where` |

`product` 테이블은 `idx_product_public_list_latest`를 사용해 `ref` 접근으로 변경되고, 정렬용 filesort 없이 인덱스 조건으로 필터링한다.

```text
product 테이블 예상 스캔 rows

인덱스 전  49,525 | ##############################
인덱스 후  24,737 | ###############                | -50.05%
```

정리하면 `V8__add_product_public_list_index.sql`의 `idx_product_public_list_latest(status, hidden, deleted_at, created_at DESC, id DESC)` 적용 후 공개 상품 필터 조건과 최신순 정렬이 같은 인덱스 경로로 처리된다. 이 데이터 기준으로는 `product` 예상 스캔량이 약 절반으로 줄고, 정렬용 filesort가 제거되어 목록·검색 조회의 DB 비용이 낮아진다.

실제 지연 시간 개선 수치는 로컬 데이터 분포, 버퍼 풀 상태, 캐시 hit 여부에 따라 달라지므로 `EXPLAIN ANALYZE` 또는 `k6/search-readonly.js` 결과로 별도 보강한다.
