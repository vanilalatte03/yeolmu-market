# ADR-014: Flyway 초기 스키마와 기본 활성화

- 상태: 채택됨
- 날짜: 2026-07-01
- 관련: ADR-009, `src/main/resources/db/migration/V1__init_schema.sql`, `docs/DEVELOPMENT.md`
- 대체: ADR-009

## 맥락

ADR-009에서는 Flyway migration을 도입했지만, 빈 DB를 처음부터 구성하는 `V1__init_schema.sql`이 없어 Flyway 기본 활성화를 유예했다.
이후 변경분 migration은 V2부터 누적되었고, 빈 MySQL 데이터베이스도 애플리케이션 시작 시 기준 스키마를 만들 수 있어야 한다.

## 결정

빈 MySQL 데이터베이스를 구성하는 `V1__init_schema.sql`을 추가하고, Flyway를 기본 활성화한다.
빈 DB에서는 V1부터 최신 migration까지 순서대로 적용한다.
테스트 환경의 일반 H2 흐름은 기존처럼 Flyway를 비활성화하되, migration 자체는 Testcontainers MySQL smoke test로 별도 검증한다.

`baseline-on-migrate`는 기본 비활성화한다.
기존 수동 생성 스키마나 Flyway 이력이 없는 DB에 연결해야 하는 환경은 자동 적용 전에 별도 baseline/repair 전략을 정하고, 필요한 경우에만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`로 명시적으로 켠다.
이 정책은 `docs/DEVELOPMENT.md`의 로컬 실행 전제와 함께 관리한다.

## 결과

- 빈 MySQL DB는 애플리케이션 시작 시 Flyway migration으로 기준 스키마를 구성할 수 있다.
- Flyway 기본값은 `SPRING_FLYWAY_ENABLED=true`이며, 필요한 환경에서만 명시적으로 끌 수 있다.
- Flyway baseline은 기본값이 false이며, 기존 DB 보정 전략이 정해진 환경에서만 opt-in 한다.
- 기존 DB에 적용할 때는 baseline 전략 검토가 선행되어야 한다.
- migration 문법과 순서는 MySQL 기반 smoke test에서 검증한다.
