# AGENTS.md

> 이 파일이 루트 단일 정본이다. `CLAUDE.md`는 `@AGENTS.md` 포인터만 두고 내용을 복제하지 않는다.
> 이 파일이 한 페이지를 넘게 비대해지면 그때 섹션을 별도 파일로 분리한다. 그 전엔 한 장으로 유지.

## 명령어

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 빌드 | `./gradlew build` | `.\gradlew.bat build` |
| 실행 | `./gradlew bootRun` | `.\gradlew.bat bootRun` |
| 실행 전제 | 로컬 MySQL/datasource 및 Redis 설정 필요 | 로컬 MySQL/datasource 및 Redis 설정 필요 |
| 로컬 인프라 | `cp .env.example .env && docker compose up -d mysql redis` | `Copy-Item .env.example .env; docker compose up -d mysql redis` |
| 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| 단일 테스트 | `./gradlew test --tests "패키지.클래스명"` | `.\gradlew.bat test --tests "패키지.클래스명"` |
| 포맷 검사 | `./gradlew spotlessCheck` | `.\gradlew.bat spotlessCheck` |
| 포맷 | `./gradlew spotlessApply` | `.\gradlew.bat spotlessApply` |

## 스택

- Java 21, Spring Boot 4.1.0, Gradle
- 보안/검증: Spring Security + Bean Validation
- DB: MySQL + Spring Data JPA (테스트는 H2)
- 캐시/검색 집계: Redis + Spring Data Redis (`spring-boot-starter-data-redis`)
- 웹: Spring MVC (`spring-boot-starter-webmvc`)
- 실시간 채팅: WebSocket + STOMP (`spring-boot-starter-websocket` 의존성 추가됨)
- 작업 완료 전 기본 검증은 `test`와 `spotlessCheck`이며, 빌드 설정·의존성·패키징 영향이 있으면 `build`까지 확인한다.

## 구조

도메인별 패키지로 구성한다.
기본 패키지 루트는 `src/main/java/com/guingujig/yeolmumarket`이다.

```
domain  - [예: user, product, order, chat, admin]  (각 도메인 안에 controller/service/repository/dto/entity)
global  - config, exception, response, security
infra   - 외부 연동 (필요 시)
```

## 컨벤션

코드 컨벤션은 `docs/CONVENTIONS.md` 한 파일에 있다. 작업 전 해당 파일을 참고한다.
제품 범위와 우선순위는 `docs/PRD.md`, API 계약은 `docs/API.md`, 데이터 모델은 `docs/ERD.md`를 정본으로 본다.
되돌리기 어려운 기술 결정은 `docs/adr/`에 ADR로 남긴다(사용법은 `docs/adr/README.md`).

## 커밋 컨벤션

- 커밋은 리뷰 가능한 의미 단위로 작게 나눈다. 기능 구현, 테스트 추가, 문서 수정, 리팩토링은 가능하면 분리한다.
- 커밋 메시지는 Conventional Commits 형식을 따른다: `type: subject`
- 허용 type은 `feat`, `fix`, `refactor`, `docs`, `test`, `chore` 중 하나다.
- subject는 한국어 한 줄로 간결하게 작성하고, 구현 내용이 바로 드러나게 현재형으로 쓴다.
  - 예: `feat: 상품 등록 API 추가`
  - 예: `fix: 채팅방 권한 검증 오류 수정`
- WIP, 임시 커밋, 서로 다른 목적이 섞인 대형 커밋은 남기지 않는다.

## PR / 이슈

- 이슈 구현을 시작할 때 해당 이슈의 Project Status를 `In progress`로 변경한다.
- PR 본문에 연결 이슈를 `Closes #N` 형식으로 명시한다.

## 스킬 / 훅 (코덱스/Claude)

- 스킬 원본은 `.agents/skills/<이름>/SKILL.md`에 둔다(repo 공유). Claude Code용 복사본은 `.claude/skills/<이름>/SKILL.md`에 두고, 미러링 대상은 `scripts/skill-sync.json`에 명시한다. 복사는 `scripts/sync-claude-skills.ps1`, 검증은 `scripts/check-skill-sync.ps1`와 CI가 담당한다.
- 현재 공유 스킬은 `review`, `pr-writer`이며 세부 트리거와 출력 형식은 각 `SKILL.md`를 따른다.
- 포맷 강제는 git pre-commit(`.githooks/pre-commit`, Spotless 검사). 클론 후 1회 `git config core.hooksPath .githooks` 실행.

## 완료 정의

작업을 끝내기 전:
- `./gradlew test` 통과
- `./gradlew spotlessCheck` 통과
- 포맷 실패 시 `./gradlew spotlessApply` 실행 후 다시 확인
- 확인하지 않은 테스트/검증을 완료로 표시하지 않는다.

## 충돌 우선순위

시스템/개발자/보안 지침이나 사용자의 명시적 요청이 이 문서와 충돌하면 그 지침을 우선한다.

## 에이전트 주의사항 (진행하며 추가)

> 에이전트가 반복적으로 틀리는 것을 여기에 추가한다.
> 규칙을 추상적으로 적지 말고, 틀린 실제 코드와 맞는 코드를 짧은 before/after 예시로 박는다.
> 관리 담당자 1명을 정하고, 나머지는 "에이전트가 또 이거 틀렸어요"만 공유한다.

- PowerShell 명령 앞에 `chcp`/`[Console]::...` 인코딩 설정을 붙이지 않는다. 파일 확인은 `Get-Content -Encoding UTF8`로 한다.
- Javadoc은 "왜"가 필요한 곳에만 단다(public Service 메서드·도메인 규칙·복잡한 분기). getter/자명한 코드엔 달지 않는다. 기준은 `docs/CONVENTIONS.md`의 Javadoc 섹션.
- Spring Boot 4를 쓴다. 코덱스가 SB3 학습데이터 기준으로 옛 starter/import를 생성하기 쉬우니 주의:
  - 의존성 ❌ `spring-boot-starter-web` / 단일 `spring-boot-starter-test`
    ✅ `spring-boot-starter-webmvc` / 모듈별 `*-test` (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-validation-test`)
  - 새 의존성·import는 추측하지 말고 `build.gradle`의 실제 모듈명을 기준으로 한다.
- 시간 값은 서버/DB/JWT 모두 UTC 기준으로 다룬다. Entity의 `LocalDateTime`은 DB `DATETIME`에 저장된 UTC 시각으로 해석하고, JWT/TTL/만료 계산은 `Instant`/epoch seconds/`Duration`을 사용한다. 수동 현재 시각이 필요하면 `LocalDateTime.now()` 대신 UTC 기준을 명시한다.
- 테스트 fixture에서 정렬·만료·시간 비교용 값을 만들 때도 `LocalDateTime.now()`를 쓰지 않는다. 현재 시각 자체를 검증하는 테스트가 아니면 고정 시각을 쓴다.
  - ❌ `ReflectionTestUtils.setField(room, "lastMessageAt", LocalDateTime.now());`
  - ✅ `LocalDateTime baseTime = LocalDateTime.of(2026, 6, 24, 10, 0);`
    `ReflectionTestUtils.setField(room, "lastMessageAt", baseTime);`
  - 현재 시각이 정말 필요하면 UTC 기준을 명시한다.
- 이슈 작업을 시작하면 **코드부터 손대지 말고 가장 먼저** 해당 이슈의 Project Status를 `In progress`로 바꾼다. 이걸 빠뜨리는 사례가 반복됐다. 규칙 원문은 위 `PR / 이슈` 섹션.
- 민감정보(DB 비밀번호, `JWT_SECRET` 등)는 코드나 `application.yml`에 하드코딩하지 않고 `.env`로 관리한다(`.env`·`.env.*`는 `.gitignore`로 제외, `.env.example`만 추적). 환경변수를 새로 추가하면 같은 커밋에서 `.env.example`에도 키를 추가한다(값은 비워 둔다).
