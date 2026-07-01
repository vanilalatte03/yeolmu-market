# ADR-009: Flyway 기반 DB 스키마 마이그레이션

- 상태: 채택됨
- 날짜: 2026-06-27
- 관련: GitHub Issue #68, `docs/ERD.md`, `docs/CONVENTIONS.md`

## 맥락

JPA 엔티티에 컬럼을 추가해도 기존 MySQL 테이블은 자동으로 변경되지 않는다.
테스트 환경은 H2와 `ddl-auto: create-drop`로 새 스키마를 만들기 때문에 통과할 수 있지만, 로컬 MySQL이나 배포 DB에는 이전 스키마가 남아 있을 수 있다.
특히 Kubernetes 배포에서는 애플리케이션 컨테이너가 새 버전으로 교체되어도 DB는 유지되므로 코드와 DB 스키마 불일치가 런타임 장애로 이어진다.

Issue #68의 `wishedAt` 응답은 `wish.created_at` 컬럼을 근거로 해야 한다.
따라서 엔티티 변경과 DB 컬럼 추가 SQL을 같은 변경 단위로 관리할 방법이 필요하다.

배포 전에는 빈 MySQL 데이터베이스를 처음부터 만들 수 있는 초기 스키마도 필요하다.

## 검토한 대안

- **수동 SQL 문서화** — 도입 비용은 작지만, 배포나 로컬 셋업에서 SQL 적용을 빠뜨릴 수 있다.
- **Hibernate DDL 자동 변경 사용** — 개발 편의성은 높지만, 운영 DB 변경을 애플리케이션 ORM에 맡겨 예측 가능성과 리뷰 가능성이 낮다.
- **Flyway 도입 후 전역 활성화** — migration 파일과 적용 이력을 관리할 수 있지만, 초기 스키마가 없는 빈 DB에서는 변경분 migration이 먼저 실행될 수 있다.
- **Flyway migration 추가 + 실행 opt-in** — 코드 변경과 DB 변경 SQL을 같은 PR에 남기되, 초기 스키마 migration이 생기기 전까지 기존 DB 보정이 필요한 환경에서만 명시적으로 실행한다.
- **V1 초기 스키마 추가 + 기존 V2-V7 보존** — 배포 전 migration 이력을 유지하면서 빈 DB가 `V1`부터 순서대로 올라오게 한다.

## 결정

DB 스키마 변경 SQL은 Flyway migration으로 관리한다.
빈 DB를 처음부터 만들 수 있도록 `V1__init_schema.sql`을 추가하고, Flyway는 기본값으로 활성화한다.
`V1`은 기존 증분 migration이 적용되기 전의 기준 스키마를 만든다.
따라서 `wish.created_at`, 주문 배송 컬럼, `review`, `product_image`, `refund_request`, `chatmessage.accepted_message_id`는 각각 기존 `V2`-`V7`에서 계속 추가한다.

Issue #68에서는 전체 초기 스키마를 재작성하지 않고 `V2__add_wish_created_at.sql`로 `wish.created_at` 컬럼만 추가한다.
`Wish`는 수정 개념이 없는 생성/삭제 중심 엔티티이므로 `modified_at`은 추가하지 않는다.

테스트 환경은 기존 H2 `ddl-auto: create-drop` 흐름을 유지하기 위해 Flyway를 비활성화한다.

## 결과

- 엔티티의 테이블/컬럼/제약조건 변경 PR은 관련 Flyway migration을 함께 포함해야 한다.
- migration 파일은 적용 이후 수정하지 않고, 변경이 필요하면 새 migration으로 보정한다.
- 빈 DB는 애플리케이션 시작 시 `V1`부터 최신 migration까지 순서대로 적용된다.
- 첫 배포 DB는 빈 스키마를 기준으로 한다. 이미 수동 생성된 DB나 Flyway 이력이 없는 기존 DB에 붙일 때는 배포 전에 별도 baseline/repair 전략을 확정한다.
- migration 버전 충돌과 롤백 SQL 부재는 감수하는 트레이드오프다.
