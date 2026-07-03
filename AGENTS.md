# AGENTS.md

> 에이전트 작업용 루트 라우터. 세부 정책은 정본 문서로 보내고, 루트엔 필수 게이트와 반복 실수 방지만 남긴다.
> `CLAUDE.md`는 `@AGENTS.md` 포인터만 두고 내용을 복제하지 않는다.

## 필수 게이트

- 실행·검증 명령과 로컬 인프라 전제는 `docs/DEVELOPMENT.md`를 따른다.
- 완료 전 `test`·`spotlessCheck` 통과 필수. 확인하지 않은 검증을 완료로 표시하지 않는다.

## 정본 라우팅

| 주제 | 정본 |
| --- | --- |
| 스택·의존성 (Java 21 / Spring Boot 4.1.0) | `build.gradle` |
| 제품 범위·우선순위 | `docs/PRD.md` |
| API 계약·공통 응답·에러 | `docs/API.md` |
| 데이터 모델 | `docs/ERD.md` |
| 개발 환경·실행·검증 게이트 | `docs/DEVELOPMENT.md` |
| 코드·커밋 컨벤션·Javadoc·시간 처리 | `docs/CONVENTIONS.md` |
| 되돌리기 어려운 기술 결정 | `docs/adr/README.md` |
| 패키지 탐색·수정 진입점 | `docs/context/source-map.md` |
| 공유 스킬·환경 셋업 | `.agents/skills/<이름>/SKILL.md`, `README.md` |

## 반복 실수 방지

- 이슈 작업은 **코드보다 먼저** 해당 이슈의 Project Status를 `In progress`로 바꾼다(빠뜨리는 사례 반복됨). PR 본문엔 연결 이슈를 `Closes #N`으로 명시한다.
- PowerShell 명령 앞에 `chcp`/`[Console]::...` 인코딩 설정을 붙이지 않는다. 파일 확인은 `Get-Content -Encoding UTF8`로 한다.
- 실행·테스트·Docker Compose 전제를 바꾸면 `AGENTS.md`에 중복 작성하지 않고 `docs/DEVELOPMENT.md`를 먼저 갱신한다.
- 민감정보(DB 비밀번호, `JWT_SECRET` 등)는 코드나 `application.yml`에 하드코딩하지 않고 `.env`로만 관리한다(`.env`·`.env.*`는 `.gitignore`, `.env.example`만 추적). 환경변수를 새로 추가하면 같은 커밋에서 `.env.example`에도 키를 추가한다(값은 비워 둔다).
- Spring Boot 4 starter/import는 학습 데이터로 추측하지 말고 항상 `build.gradle`의 실제 모듈명을 기준으로 한다.
