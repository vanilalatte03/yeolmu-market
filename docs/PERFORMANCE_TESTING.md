# Performance Testing

성능 테스트용 데이터 적재와 k6 수동 검증 절차의 정본이다. 이 절차는 Gradle `test`와 완료 게이트에 자동 포함되지 않는다.

## 성능 테스트용 더미 데이터 적재

상품 검색 성능 테스트용 대용량 더미 데이터는 `src/test`의 수동 테스트로만 적재한다. 일반 `test`에서는 실행되지 않고, `YEOLMU_PERF_SEED=true` 환경변수를 명시한 경우에만 `JdbcTemplate.batchUpdate`로 로컬 MySQL에 데이터를 넣는다.

수동 테스트는 `perf-seed` profile로 실행되며 `.env`의 `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*` 값을 사용한다. 기본값은 관계형 테이블별 50,000건, batch size 1,000이다. 대상 테이블은 `users`, `category`, `product`, `product_image`, `wish`, `chatroom`, `chatmessage`, `orders`, `payment`, `refund_request`, `review`다. 적재가 끝나면 검색 인덱스 버전을 증가시킨다.

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 기본 적재 | `YEOLMU_PERF_SEED=true ./gradlew test --tests "com.guingujig.yeolmumarket.support.PerfDummyDataSeedTest"` | `$env:YEOLMU_PERF_SEED='true'; .\gradlew.bat test --tests "com.guingujig.yeolmumarket.support.PerfDummyDataSeedTest"; Remove-Item Env:\YEOLMU_PERF_SEED` |
| 100,000건 적재 | `YEOLMU_PERF_SEED=true YEOLMU_PERF_DUMMY_DATA_TABLE_ROW_COUNT=100000 ./gradlew test --tests "com.guingujig.yeolmumarket.support.PerfDummyDataSeedTest"` | `$env:YEOLMU_PERF_SEED='true'; $env:YEOLMU_PERF_DUMMY_DATA_TABLE_ROW_COUNT='100000'; .\gradlew.bat test --tests "com.guingujig.yeolmumarket.support.PerfDummyDataSeedTest"; Remove-Item Env:\YEOLMU_PERF_SEED; Remove-Item Env:\YEOLMU_PERF_DUMMY_DATA_TABLE_ROW_COUNT` |

반복 실행하면 모든 대상 테이블에 새 run key로 추가 적재한다. 실행 전 로컬 MySQL과 Redis는 `docker compose up -d mysql redis`로 먼저 띄운다.

## 수동 k6 성능 테스트

k6 성능 테스트는 Gradle `test`에 포함하지 않는 수동 검증이다. 로컬 MySQL과 Redis를 띄우고, 성능 테스트용 더미 데이터를 적재한 뒤, 애플리케이션을 `bootRun`으로 실행한 상태에서 별도 터미널로 실행한다.

공개 읽기, 인증, 상태 변경, 주문 동시성, WebSocket은 서로 다른 병목과 데이터 정합성 조건을 보므로 스크립트를 분리한다. 모든 스크립트는 기본적으로 `BASE_URL=http://localhost:8080`, `SUMMARY_DIRECTORY=k6/results`를 사용하며, 실행이 끝나면 `k6/results/{SUMMARY_BASENAME}.md`와 `k6/results/{SUMMARY_BASENAME}.json`을 저장한다. 결과 산출물은 Git 추적에서 제외한다.

성능 seed 사용자는 `perf-user-{runKey}-{00001부터 5자리 sequence}@example.com` 형식이며 기본 비밀번호는 `password`다. `runKey`는 `PerfDummyDataSeedTest` 실행 로그의 `runKey=...` 값을 사용한다. `PERF_RUN_KEY`를 지정하면 k6 스크립트가 이 패턴으로 계정을 만든다. seed 계정을 쓰지 않으면 `AUTH_EMAILS`, `BUYER_EMAILS`, `SELLER_EMAIL`, `CHAT_USER_EMAILS` 같은 스크립트별 계정 환경변수를 명시한다. 단, 자동 회원가입을 지원하는 스크립트는 별도 설명의 기본값을 따른다.

현재 k6 스크립트는 다음과 같다.

| 스크립트 | 목적 | 주요 API |
| --- | --- | --- |
| `k6/search-readonly.js` | 검색 읽기 부하 | `GET /api/products`, `GET /api/search/v2/products`, `GET /api/search/popular-keywords` |
| `k6/catalog-readonly.js` | 상품 목록/상세, 카테고리별 목록, 랜덤 상세 조회 | `GET /api/products`, `GET /api/products/{productId}`, `GET /api/categories/{categoryId}/products` |
| `k6/auth-session.js` | 로그인/리프레시 분리 부하 | `POST /api/auth/login`, `POST /api/auth/refresh` |
| `k6/wish-toggle.js` | 찜하기/찜 취소 write 부하 | `POST /api/products/{productId}/wishes`, `DELETE /api/products/{productId}/wishes` |
| `k6/order-concurrency.js` | 동일 상품 동시 주문 정합성 | `POST /api/products/{productId}/orders` |
| `k6/chat-websocket.js` | STOMP WebSocket 연결/메시지 처리량 | `/ws`, `/pub/chat-rooms/{roomId}/message` |

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| k6 설치 확인 | `k6 version` | `k6 version` |
| 검색 기본 실행 | `k6 run k6/search-readonly.js` | `k6 run k6/search-readonly.js` |
| 상품 탐색 기본 실행 | `k6 run k6/catalog-readonly.js` | `k6 run k6/catalog-readonly.js` |
| 부하 조정 실행 | `BASE_URL=http://localhost:8080 VUS=30 DURATION=3m k6 run k6/catalog-readonly.js` | `$env:BASE_URL='http://localhost:8080'; $env:VUS='30'; $env:DURATION='3m'; k6 run k6/catalog-readonly.js; Remove-Item Env:\BASE_URL; Remove-Item Env:\VUS; Remove-Item Env:\DURATION` |
| VU 점진 증가 실행 | `VUS=30 RAMP_UP_DURATION=30s DURATION=3m k6 run k6/catalog-readonly.js` | `$env:VUS='30'; $env:RAMP_UP_DURATION='30s'; $env:DURATION='3m'; k6 run k6/catalog-readonly.js; Remove-Item Env:\VUS; Remove-Item Env:\RAMP_UP_DURATION; Remove-Item Env:\DURATION` |
| 결과 파일명 변경 | `SUMMARY_BASENAME=catalog-vus30 k6 run k6/catalog-readonly.js` | `$env:SUMMARY_BASENAME='catalog-vus30'; k6 run k6/catalog-readonly.js; Remove-Item Env:\SUMMARY_BASENAME` |

`search-readonly.js`, `catalog-readonly.js`, `auth-session.js`, `wish-toggle.js`는 `RAMP_UP_DURATION`을 지정하면 `ramping-vus` executor로 전환되어 0 VU에서 `VUS`까지 점진 증가한 뒤 `DURATION` 동안 유지하고, `RAMP_DOWN_DURATION` 기본값 `10s` 동안 0 VU로 줄인다.

상품 탐색 스크립트는 `PRODUCT_IDS` 또는 `PRODUCT_ID_MIN`/`PRODUCT_ID_MAX`를 지정하면 랜덤 상세 조회에 해당 ID를 사용한다. 지정하지 않으면 setup 단계에서 상품 목록 앞쪽 일부를 샘플링한다. 카테고리는 `CATEGORY_IDS`를 지정하거나 `GET /api/categories` 응답을 사용한다.

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 상품 ID 범위 기반 랜덤 상세 | `PRODUCT_ID_MIN=1 PRODUCT_ID_MAX=50000 k6 run k6/catalog-readonly.js` | `$env:PRODUCT_ID_MIN='1'; $env:PRODUCT_ID_MAX='50000'; k6 run k6/catalog-readonly.js; Remove-Item Env:\PRODUCT_ID_MIN; Remove-Item Env:\PRODUCT_ID_MAX` |
| 인증 로그인/리프레시 | `PERF_RUN_KEY=260701abcdef VUS=30 k6 run k6/auth-session.js` | `$env:PERF_RUN_KEY='260701abcdef'; $env:VUS='30'; k6 run k6/auth-session.js; Remove-Item Env:\PERF_RUN_KEY; Remove-Item Env:\VUS` |
| 찜 토글 자동 회원가입 | `VUS=30 k6 run k6/wish-toggle.js` | `$env:VUS='30'; k6 run k6/wish-toggle.js; Remove-Item Env:\VUS` |
| 찜 토글 seed 계정 | `PERF_RUN_KEY=260701abcdef VUS=30 k6 run k6/wish-toggle.js` | `$env:PERF_RUN_KEY='260701abcdef'; $env:VUS='30'; k6 run k6/wish-toggle.js; Remove-Item Env:\PERF_RUN_KEY; Remove-Item Env:\VUS` |
| 주문 동시성 자동 회원가입 | `CONCURRENCY=20 k6 run k6/order-concurrency.js` | `$env:CONCURRENCY='20'; k6 run k6/order-concurrency.js; Remove-Item Env:\CONCURRENCY` |
| 주문 동시성 seed 계정 | `PERF_RUN_KEY=260701abcdef CONCURRENCY=20 k6 run k6/order-concurrency.js` | `$env:PERF_RUN_KEY='260701abcdef'; $env:CONCURRENCY='20'; k6 run k6/order-concurrency.js; Remove-Item Env:\PERF_RUN_KEY; Remove-Item Env:\CONCURRENCY` |
| 채팅 WebSocket 자동 회원가입 | `VUS=10 k6 run k6/chat-websocket.js` | `$env:VUS='10'; k6 run k6/chat-websocket.js; Remove-Item Env:\VUS` |
| 채팅 WebSocket seed 계정 | `PERF_RUN_KEY=260701abcdef VUS=30 k6 run k6/chat-websocket.js` | `$env:PERF_RUN_KEY='260701abcdef'; $env:VUS='30'; k6 run k6/chat-websocket.js; Remove-Item Env:\PERF_RUN_KEY; Remove-Item Env:\VUS` |

`auth-session.js`는 refresh token 회전 정책 때문에 VU마다 다른 계정을 써야 한다. `AUTH_EMAIL`, `AUTH_EMAILS`, `LOGIN_EMAIL`, `PERF_RUN_KEY`를 모두 생략하면 setup 단계에서 `k6-auth-...@example.com` 형식의 계정을 `VUS` 수만큼 자동 회원가입하고 그 계정으로 로그인/리프레시 부하를 실행한다. 기존 계정을 쓰려면 `AUTH_EMAIL`, `AUTH_PASSWORD`를 지정하거나 성능 seed 후 `PERF_RUN_KEY`를 지정한다. `VUS`를 2 이상으로 올리면서 기존 계정을 쓸 때는 `PERF_RUN_KEY`로 `VUS` 수만큼 seed 계정을 사용하거나 `AUTH_EMAILS`에 `VUS`개 이상의 이메일을 넣는다. 자동 회원가입을 끄려면 `AUTO_SIGNUP_AUTH_USERS=false`, 실행 초반 계정 검증을 끄려면 `VALIDATE_AUTH_SETUP=false`, 실패 응답을 반복 출력하려면 `LOG_FAILURES=true`를 사용한다.

`wish-toggle.js`는 계정별 찜 상태 충돌을 줄이기 위해 VU마다 다른 계정을 쓴다. `AUTH_EMAIL`, `AUTH_EMAILS`, `LOGIN_EMAIL`, `PERF_RUN_KEY`를 모두 생략하면 `AUTO_SIGNUP_WISH_USERS=true` 기본값으로 `k6-wish-...@example.com` 형식의 계정을 `VUS` 수만큼 자동 회원가입하고 로그인한다. 기존 계정을 쓰려면 `PERF_RUN_KEY`로 `VUS` 수만큼 seed 계정을 사용하거나 `AUTH_EMAILS`에 `VUS`개 이상의 이메일을 넣는다. 자동 회원가입을 끄려면 `AUTO_SIGNUP_WISH_USERS=false`를 사용한다. 기본값 `CLEANUP_BEFORE_CREATE=true`로 먼저 `DELETE`를 보내 초기 찜 상태를 정리한 뒤 `POST`와 `DELETE`를 측정한다. 완전히 깨끗한 전용 유저/상품 조합을 준비했다면 `CLEANUP_BEFORE_CREATE=false`로 cleanup 요청을 제거할 수 있다.

`order-concurrency.js`는 지속 부하가 아니라 정합성 테스트다. `per-vu-iterations`로 같은 상품에 `CONCURRENCY`건을 보내며 threshold는 `201 Created` 1건, `409 Conflict` `CONCURRENCY-1`건을 기대한다. `BUYER_EMAILS`, `SELLER_EMAIL`, `PERF_RUN_KEY`를 모두 생략하면 `AUTO_SIGNUP_ORDER_USERS=true` 기본값으로 구매자 `CONCURRENCY`명과 판매자 1명을 자동 회원가입한다. `ORDER_PRODUCT_ID`를 지정하지 않으면 setup 단계에서 판매자 계정으로 새 상품을 만든다. 자동 회원가입을 끄려면 `AUTO_SIGNUP_ORDER_USERS=false`를 사용한다. `PERF_RUN_KEY` 사용 시 판매자는 sequence 1, 구매자는 sequence 2부터 사용하므로 최소 `CONCURRENCY + 1`명의 seed 사용자가 필요하다.

`chat-websocket.js`는 k6 raw WebSocket 위에 STOMP 프레임을 직접 전송한다. `CHAT_ROOM_IDS`를 지정하면 해당 방을 사용하고, 지정하지 않으면 setup 단계에서 판매자 상품 1개와 VU별 채팅방을 만든다. `CHAT_USER_EMAILS`, `CHAT_USER_EMAIL`, `SELLER_EMAIL`, `PERF_RUN_KEY`를 모두 생략하면 `AUTO_SIGNUP_CHAT_USERS=true` 기본값으로 판매자 1명과 VU 수만큼 채팅 사용자를 자동 회원가입한 뒤 로그인한다. 자동 회원가입을 끄려면 `AUTO_SIGNUP_CHAT_USERS=false`를 사용한다. `PERF_RUN_KEY` 사용 시 판매자는 sequence 1, 채팅 사용자는 sequence 2부터 사용한다. 기존 채팅방을 지정하는 경우 각 `CHAT_USER_EMAILS` 계정은 해당 방의 참여자여야 한다. 구독 receipt를 기다린 뒤 메시지를 보내며, receipt가 늦는 환경에서는 `SUBSCRIBE_READY_TIMEOUT_MS` 이후 전송을 시작한다.
