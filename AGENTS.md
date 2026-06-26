# AGENTS.md

> 에이전트 작업용 루트 라우터. 세부 정책은 정본 문서로 보내고, 루트엔 실행 게이트와 반복 실수 방지만 남긴다.
> `CLAUDE.md`는 `@AGENTS.md` 포인터만 두고 내용을 복제하지 않는다.

## 명령어 (실행 게이트)

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 빌드 | `./gradlew build` | `.\gradlew.bat build` |
| 실행 | `./gradlew bootRun` | `.\gradlew.bat bootRun` |
| 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| 단일 테스트 | `./gradlew test --tests "패키지.클래스명"` | `.\gradlew.bat test --tests "패키지.클래스명"` |
| 포맷 검사 | `./gradlew spotlessCheck` | `.\gradlew.bat spotlessCheck` |
| 포맷 적용 | `./gradlew spotlessApply` | `.\gradlew.bat spotlessApply` |

- 실행 전제: 로컬 MySQL/datasource·Redis. macOS/Linux `cp .env.example .env && docker compose up -d mysql redis` / PowerShell `Copy-Item .env.example .env; docker compose up -d mysql redis`
- 완료 전 `test`·`spotlessCheck` 통과 필수(실패 시 `spotlessApply` 후 재확인). 확인하지 않은 검증을 완료로 표시하지 않는다.

## 정본 라우팅

| 주제 | 정본 |
| --- | --- |
| 스택·의존성 (Java 21 / Spring Boot 4.1.0) | `build.gradle` |
| 제품 범위·우선순위 | `docs/PRD.md` |
| API 계약·공통 응답·에러 | `docs/API.md` |
| 데이터 모델 | `docs/ERD.md` |
| 코드·커밋 컨벤션·Javadoc·시간 처리 | `docs/CONVENTIONS.md` |
| 되돌리기 어려운 기술 결정 | `docs/adr/README.md` |
| 패키지 탐색·수정 진입점 | `docs/context/source-map.md` |
| 공유 스킬·환경 셋업 | `.agents/skills/<이름>/SKILL.md`, `README.md` |

## 반복 실수 방지

- 이슈 작업은 **코드보다 먼저** 해당 이슈의 Project Status를 `In progress`로 바꾼다(빠뜨리는 사례 반복됨). PR 본문엔 연결 이슈를 `Closes #N`으로 명시한다.
- PowerShell 명령 앞에 `chcp`/`[Console]::...` 인코딩 설정을 붙이지 않는다. 파일 확인은 `Get-Content -Encoding UTF8`로 한다.
- 민감정보(DB 비밀번호, `JWT_SECRET` 등)는 코드나 `application.yml`에 하드코딩하지 않고 `.env`로만 관리한다(`.env`·`.env.*`는 `.gitignore`, `.env.example`만 추적). 환경변수를 새로 추가하면 같은 커밋에서 `.env.example`에도 키를 추가한다(값은 비워 둔다).
- Spring Boot 4 starter/import는 학습 데이터로 추측하지 말고 항상 `build.gradle`의 실제 모듈명을 기준으로 한다.
