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
- [CONVENTIONS](docs/CONVENTIONS.md) — 코드 컨벤션
- [ADR](docs/adr/README.md) — 기술 결정 기록

## 개발

[AGENTS.md](AGENTS.md)는 에이전트 작업 라우터다. 빌드·실행·테스트 명령과 필수 운영 게이트는 [DEVELOPMENT](docs/DEVELOPMENT.md)를 보고, 세부 정본은 `docs/*` 문서를 따른다.

### 환경 셋업

로컬 인프라와 검증 명령은 [DEVELOPMENT](docs/DEVELOPMENT.md)를 따른다.

### 스킬

- 원본은 `.agents/skills/<이름>/SKILL.md`, Claude Code용 복사본은 `.claude/skills/<이름>/SKILL.md`에 둔다.
- 복사·검증은 `scripts/sync-claude-skills.ps1`, `scripts/check-skill-sync.ps1`, CI가 담당한다.
- 현재 공유 스킬은 `review`, `pr-writer`. 세부 트리거·출력 형식은 각 `SKILL.md`를 따른다.
