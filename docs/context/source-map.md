# Source Map

이 문서는 코드 작업을 시작할 때의 탐색 지도다. 계약·정책 자체는 복제하지 않고 각 정본 문서로 연결한다.

기본 패키지 루트는 `src/main/java/com/guingujig/yeolmumarket`이다.

| 작업 영역 | 먼저 볼 위치 | 함께 확인할 정본 |
| --- | --- | --- |
| auth/security | `domain/auth`, `global/security` | `docs/API.md`, `docs/adr/012-jwt-revocation-degraded-mode.md`, `.env.example` |
| user | `domain/user` | `docs/API.md`, `docs/ERD.md` |
| product/admin | `domain/product`, `domain/category`, `domain/wish` | `docs/API.md`, `docs/ERD.md`, `docs/adr/006-product-hidden-flag.md`, `docs/adr/001-concurrent-order-control.md` |
| search/product cache | `domain/search`, `domain/product` | `docs/API.md`, `docs/adr/002-redis-search-cache.md`, `docs/adr/011-product-search-cache-partial-eviction.md` |
| chat/websocket | `domain/chat`, `global/config/WebSocketConfig.java` | `docs/API.md`, `docs/ERD.md` |
| order/payment/refund/review | `domain/order`, `domain/payment`, `domain/refund`, `domain/review` | `docs/API.md`, `docs/ERD.md`, `docs/adr/001-concurrent-order-control.md`, `docs/adr/005-mock-safe-payment-transaction-policy.md`, `docs/adr/013-distributed-lock-key-and-lease.md` |
| global | `global/exception`, `global/response`, `global/config`, `global/security`, `global/entity` | `docs/API.md`, `docs/CONVENTIONS.md` |
| development/validation | `build.gradle`, `docker-compose.yml`, `.githooks`, `gradlew.bat` | `docs/DEVELOPMENT.md` |
| demo seed | `global/seed`, `src/main/resources/application-demo-seed.yml`, `src/main/resources/db/migration/V9__seed_default_categories.sql` | `docs/DEMO_SEED.md`, `.env.example` |
| performance/k6 | `src/test/java/com/guingujig/yeolmumarket/support/PerfDummyDataSeedTest.java`, `k6` | `docs/PERFORMANCE_TESTING.md`, `docs/DEVELOPMENT.md` |
| test | `src/test/java/com/guingujig/yeolmumarket` | `docs/CONVENTIONS.md`, `docs/DEVELOPMENT.md` |

도메인 내부는 가능한 한 `controller / service / repository / dto / entity` 흐름을 따른다.
새 파일을 추가할 때는 기존 같은 도메인의 이름·패키지·테스트 배치를 먼저 맞춘다.
