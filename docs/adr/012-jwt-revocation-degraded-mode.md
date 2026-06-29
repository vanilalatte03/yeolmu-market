# ADR-012: JWT 폐기 검증 degraded mode와 refresh token 회전 정책

- 상태: 채택됨
- 날짜: 2026-06-29
- 관련: `docs/PRD.md` (7.1 회원), `docs/API.md` (인증 API), `docs/ERD.md` (Redis 데이터 제외)
- 대체: ADR-007

## 맥락

ADR-007은 로그아웃된 access token을 Redis 블랙리스트로 폐기하고, 인증 필터가 매 요청마다 블랙리스트 등록 여부를 확인하기로 결정했다.
이 방식은 폐기된 access token을 만료 전에도 차단할 수 있지만, Redis 조회 실패가 보호 API 전체 인증 실패로 전파된다.

열무마켓에서 Redis는 검색 캐시, 인기 검색어, 토큰 폐기와 refresh token 활성 상태 관리에 사용한다.
이 중 refresh token 활성 상태는 로그인 연장과 토큰 회전의 정본에 가깝지만, access token 블랙리스트는 이미 발급된 짧은 수명의 token을 보완적으로 폐기하기 위한 데이터다.
따라서 Redis 장애가 발생했을 때 일반 보호 API 전체가 중단되는 것보다, access token 폐기 검증만 일시적으로 약화하고 서비스 가용성을 유지하는 정책이 더 적합하다.

## 검토한 대안

- **fail-closed 유지** — Redis 블랙리스트 조회에 실패하면 인증을 실패시킨다. 폐기된 access token 재사용을 가장 강하게 막지만, Redis 장애가 보호 API 전체 장애로 번진다.
- **access token 블랙리스트 제거** — access token 인증을 서명과 만료만으로 처리한다. 구조가 단순하고 Redis 장애 영향이 사라지지만, Redis가 정상일 때도 로그아웃된 access token을 만료 전 차단할 수 없다.
- **degraded mode 적용** — Redis가 정상일 때는 블랙리스트를 확인하고, 조회 실패 시에만 서명과 만료 검증으로 인증을 진행한다. 장애 중 폐기 토큰 재사용 가능성을 감수하는 대신, Redis 장애가 일반 보호 API 전체 장애가 되는 것을 막는다.

## 결정

access token 인증은 JWT 서명, 만료, token type을 먼저 검증한다.
Redis 블랙리스트가 정상 조회되면 등록 여부를 확인하고, 등록된 access token은 `REVOKED_TOKEN` 인증 실패로 처리한다.

Redis 블랙리스트 조회가 실패하면 access token 인증은 degraded mode로 전환한다.
이때 서버는 장애를 로그로 남기고, access token의 서명과 만료가 유효하면 인증을 통과시킨다.
즉 Redis 장애 중에는 이미 로그아웃되어 블랙리스트에 등록된 access token이 만료 전까지 일시적으로 사용될 수 있다.

refresh token은 Redis에 활성 token 식별자를 저장해 서버에서 관리한다.
사용자별 활성 refresh token은 1개만 허용하며, 로그인 또는 refresh token 재발급 성공 시 기존 활성 refresh token을 새 값으로 교체한다.
refresh token 조회, 저장, 회전, 삭제에 실패하면 토큰 발급 또는 상태 변경을 완료할 수 없으므로 `REDIS_UNAVAILABLE`로 실패시킨다.

로그아웃은 요청 access token을 Redis 블랙리스트에 등록하고, 인증된 회원의 활성 refresh token을 Redis에서 삭제한다.
로그아웃 중 Redis 쓰기 또는 삭제가 실패하면 로그아웃 완료 여부를 보장할 수 없으므로 `REDIS_UNAVAILABLE`로 실패시킨다.

Redis 키나 값에는 원문 token을 저장하지 않는다.
구현 시 원문 token의 해시 또는 `jti` 같은 token 식별자를 사용한다.
MySQL은 회원과 거래 데이터의 정본으로 유지하고, access token 블랙리스트와 활성 refresh token은 관계형 ERD에 포함하지 않는다.

## 결과

- Redis가 정상인 동안 로그아웃된 access token은 만료 전이라도 보호 API 인증에 사용할 수 없다.
- Redis 블랙리스트 조회 실패는 보호 API 전체 장애로 전파하지 않는다.
- Redis 장애 중에는 access token 폐기 검증이 일시적으로 약해지며, 짧은 access token TTL로 위험 시간을 제한한다.
- refresh token 재발급 API는 폐기되었거나 현재 활성 token이 아닌 refresh token에 `REVOKED_TOKEN`을 반환할 수 있다.
- refresh token 저장, 조회, 회전, 삭제 실패는 `REDIS_UNAVAILABLE`로 처리한다.
- 로그인, refresh, 로그아웃처럼 token 상태를 생성하거나 변경하는 API는 Redis 없이는 성공 처리하지 않는다.
