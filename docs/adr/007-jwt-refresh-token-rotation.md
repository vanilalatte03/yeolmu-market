# ADR-007: Redis 기반 JWT 폐기와 refresh token 회전 정책

- 상태: 채택됨
- 날짜: 2026-06-24
- 관련: `docs/PRD.md` (7.1 회원), `docs/API.md` (인증 API), `docs/ERD.md` (Redis 데이터 제외)
- 대체: ADR-004

## 맥락

ADR-004는 로그아웃된 access token을 Redis 블랙리스트로 폐기하기로 결정했다.

P0 인증 범위에 refresh token 재발급이 포함되면서, access token만 폐기하면 로그아웃 뒤에도 refresh token으로 새 access token을 발급받을 수 있다.
따라서 refresh token도 서버가 활성 상태를 관리하고, 재발급과 로그아웃 시 폐기해야 한다.

## 검토한 대안

- **클라이언트 토큰 삭제만 사용** — 구현이 가장 단순하다. 다만 탈취되었거나 다른 저장소에 남은 access token과 refresh token은 만료 전까지 계속 유효하다.
- **MySQL 폐기 토큰 테이블** — 서버가 폐기 상태를 영속적으로 확인할 수 있다. 다만 모든 인증 요청에서 관계형 DB 조회 또는 별도 캐시 동기화가 필요하고, 만료 토큰 정리 작업이 추가된다.
- **Redis access token 블랙리스트 + 활성 refresh token 관리** — 서버 인스턴스 간 폐기 상태를 공유할 수 있고 TTL로 토큰 만료 시점에 맞춰 자동 제거할 수 있다. 대신 인증과 재발급 경로에 Redis 조회가 추가된다.

## 결정

로그아웃 요청에 사용된 JWT access token은 Redis 블랙리스트에 등록한다.
블랙리스트 항목의 TTL은 해당 JWT의 만료까지 남은 시간으로 설정한다.
TTL이 끝나면 Redis가 블랙리스트 항목을 자동 제거한다.

인증 필터는 JWT 서명과 만료 여부를 검증한 뒤 Redis 블랙리스트 등록 여부를 확인한다.
블랙리스트에 등록된 토큰은 만료 전이라도 인증 실패로 처리한다.

refresh token은 Redis에 활성 토큰 해시 또는 `jti` 같은 토큰 식별자를 저장해 서버에서 관리한다.
이번 프로젝트는 사용자별 활성 refresh token을 1개만 허용한다.
refresh token 항목의 TTL은 해당 refresh token의 만료까지 남은 시간으로 설정한다.

로그인 성공 시 서버는 새 refresh token을 발급하고 Redis의 해당 사용자 활성 refresh token을 새 값으로 교체한다.
refresh token 재발급 성공 시 서버는 요청 refresh token이 Redis의 활성 값과 일치하는지 확인한 뒤, 기존 값을 폐기하고 새 refresh token 값을 저장한다.
로그아웃 시 서버는 요청 access token을 블랙리스트에 등록하고, 인증된 회원의 활성 refresh token을 Redis에서 삭제한다.

Redis 키나 값에는 원문 토큰을 저장하지 않는다.
구현 시 원문 토큰의 해시 또는 `jti` 같은 토큰 식별자를 사용한다.
MySQL은 회원과 거래 데이터의 정본으로 유지하고, access token 블랙리스트와 활성 refresh token은 관계형 ERD에 포함하지 않는다.

## 결과

- 로그아웃은 클라이언트 로컬 삭제만으로 완료되지 않고 서버 블랙리스트 등록과 활성 refresh token 삭제까지 포함한다.
- 같은 access token으로 로그아웃을 반복 호출하면 이미 폐기된 토큰으로 보아 인증 실패가 발생할 수 있다.
- refresh token은 재발급 성공 시 회전한다. 이전 refresh token은 만료 전이라도 더 이상 사용할 수 없다.
- 새 로그인은 기존 활성 refresh token을 대체한다.
- 인증이 필요한 모든 API는 `REVOKED_TOKEN` 상태를 공통 401 에러로 반환할 수 있다.
- refresh token 재발급 API도 폐기되었거나 현재 활성 토큰이 아닌 refresh token에 `REVOKED_TOKEN`을 반환할 수 있다.
- 검색 캐시와 달리 토큰 폐기 정보는 인증 보안 경로에 있으므로 Redis 조회 실패를 단순 캐시 미스로 취급하지 않는다.
