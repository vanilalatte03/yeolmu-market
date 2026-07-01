# Development

이 문서는 로컬 개발 환경과 실행·검증 명령의 정본이다. 실행, 테스트, Docker Compose 전제가 바뀌면 다른 문서에 중복 작성하지 않고 이 문서를 먼저 갱신한다.

## 전제 도구

- Java 21
- Docker Compose
- macOS/Linux는 `./gradlew`, Windows PowerShell은 `.\gradlew.bat`를 사용한다.

## 로컬 인프라

애플리케이션 로컬 실행과 수동 API 시나리오는 MySQL datasource와 Redis를 전제로 한다. 인프라는 루트 `docker-compose.yml`로 실행한다.

| 서비스 | 이미지 | 공개 포트 | 용도 |
| --- | --- | --- | --- |
| `mysql` | `mysql:8.4` | `${MYSQL_PORT:-3306}:3306` | 애플리케이션 datasource |
| `redis` | `redis:7.4-alpine` | `6379:6379` | JWT 상태, 검색 캐시, 인기 검색어 |

별도 Docker 네트워크를 만들지 않고 Docker Compose 기본 네트워크를 사용한다. 애플리케이션은 컨테이너가 아니라 호스트에서 `bootRun`으로 실행하므로 `.env`의 DB/Redis host는 로컬 공개 포트 기준으로 맞춘다.

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/yeolmu_market
SPRING_DATASOURCE_USERNAME=yeolmu
SPRING_DATASOURCE_PASSWORD=local-password
SPRING_FLYWAY_ENABLED=true
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
```

`MYSQL_PORT`를 `3307`처럼 바꾸면 `SPRING_DATASOURCE_URL`의 포트도 같은 값으로 맞춘다. Redis 포트는 현재 `docker-compose.yml`에서 `6379:6379`로 고정되어 있으므로 포트 충돌이 나면 로컬 Redis를 중지하거나 compose 포트와 `.env` 값을 함께 바꾼다.
Flyway는 기본 활성화되어 있으며, 빈 MySQL 데이터베이스는 애플리케이션 시작 시 `V1__init_schema.sql`부터 최신 migration까지 순서대로 적용된다.
이미 수동으로 만든 스키마나 Flyway 이력이 없는 기존 DB에 연결해야 한다면 먼저 별도 baseline/repair 전략을 정한 뒤 실행한다.

## 초기 셋업

| 환경 | 명령 |
| --- | --- |
| macOS/Linux | `cp .env.example .env` |
| Windows PowerShell | `Copy-Item .env.example .env` |

`.env`를 복사한 뒤 로컬 값을 채운다. DB 비밀번호와 `JWT_SECRET` 같은 민감정보는 코드나 `application.yml`에 넣지 않는다.

인프라를 시작한다.

| 환경 | 명령 |
| --- | --- |
| macOS/Linux | `docker compose up -d mysql redis` |
| Windows PowerShell | `docker compose up -d mysql redis` |

상태와 로그는 아래 명령으로 확인한다.

| 작업 | 명령 |
| --- | --- |
| 컨테이너 상태 확인 | `docker compose ps` |
| MySQL/Redis 로그 확인 | `docker compose logs -f mysql redis` |
| 컨테이너 중지 | `docker compose down` |
| 컨테이너와 로컬 볼륨 삭제 | `docker compose down -v` |

클론 후 1회 `git config core.hooksPath .githooks`를 실행한다. pre-commit에서 Spotless 포맷 검사가 동작한다.

## 애플리케이션 실행

로컬 애플리케이션은 `http://localhost:8080`에서 실행한다. 실행 전에 MySQL과 Redis 컨테이너가 떠 있어야 한다.

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 빌드 | `./gradlew build` | `.\gradlew.bat build` |
| 실행 | `./gradlew bootRun` | `.\gradlew.bat bootRun` |

## 컨테이너 이미지

애플리케이션 이미지는 루트 `Dockerfile`로 빌드한다.
빌드 컨텍스트에는 `.env`와 `.env.*`를 포함하지 않으며, datasource, Redis, JWT 같은 환경값은 컨테이너 실행 시 환경변수로 주입한다.

```bash
docker build -t yeolmu-market:local .
```

이미지에는 `/actuator/health/liveness`를 확인하는 Docker `HEALTHCHECK`가 포함되어 있다.

## 테스트와 포맷

자동 테스트는 `src/test/resources/application.yml`을 사용한다. 테스트 DB는 H2 인메모리이고 Flyway는 비활성화되어 있다. 대부분의 Redis 연동 지점은 테스트에서 mock 또는 fallback 검증으로 다룬다.

단, 분산 락(Redisson)의 운영 wiring(starter가 `spring.data.redis.*`로 빈 생성)과 실제 락 동작(획득 실패 `CONFLICT`, lease 경계)은 mock으로 대체할 수 없어 **실 Redis로 검증한다**(ADR-013). 이 테스트들은 `YEOLMU_REDIS_LOCK_TEST=true`와 도달 가능한 Redis를 전제로 하며, CI(`.github/workflows/ci.yml`)가 Redis service container와 환경변수를 제공한다. 로컬에서 실행하려면 `docker compose up -d redis`로 Redis를 띄운 뒤 `YEOLMU_REDIS_LOCK_TEST=true`를 설정한다. 환경변수가 없으면 해당 테스트는 자동으로 건너뛴다.

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| 단일 테스트 | `./gradlew test --tests "패키지.클래스명"` | `.\gradlew.bat test --tests "패키지.클래스명"` |
| 포맷 검사 | `./gradlew spotlessCheck` | `.\gradlew.bat spotlessCheck` |
| 포맷 적용 | `./gradlew spotlessApply` | `.\gradlew.bat spotlessApply` |

수동/반자동 API 검증은 `docs/P0_TEST_SCENARIOS.md`, `docs/P1_TEST_SCENARIOS.md`, `docs/P2_TEST_SCENARIOS.md`를 따른다. 이 시나리오들은 로컬 MySQL, Redis, `http://localhost:8080`에서 실행 중인 애플리케이션을 전제로 한다.

## 완료 게이트

- 완료 전 `test`와 `spotlessCheck` 통과를 확인한다.
- `spotlessCheck`가 실패하면 `spotlessApply`를 실행한 뒤 `test`와 `spotlessCheck`를 다시 확인한다.
- 확인하지 않은 검증을 완료로 표시하지 않는다.
