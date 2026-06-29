# Development

이 문서는 로컬 개발 환경과 실행·검증 명령의 정본이다.

## 환경 셋업

로컬 실행과 일부 테스트는 MySQL datasource와 Redis를 전제로 한다.

| 환경 | 명령 |
| --- | --- |
| macOS/Linux | `cp .env.example .env && docker compose up -d mysql redis` |
| Windows PowerShell | `Copy-Item .env.example .env; docker compose up -d mysql redis` |

클론 후 1회 `git config core.hooksPath .githooks`를 실행한다. pre-commit에서 Spotless 포맷 검사가 동작한다.

## 명령어

| 작업 | macOS/Linux | Windows PowerShell |
| --- | --- | --- |
| 빌드 | `./gradlew build` | `.\gradlew.bat build` |
| 실행 | `./gradlew bootRun` | `.\gradlew.bat bootRun` |
| 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| 단일 테스트 | `./gradlew test --tests "패키지.클래스명"` | `.\gradlew.bat test --tests "패키지.클래스명"` |
| 포맷 검사 | `./gradlew spotlessCheck` | `.\gradlew.bat spotlessCheck` |
| 포맷 적용 | `./gradlew spotlessApply` | `.\gradlew.bat spotlessApply` |

## 완료 게이트

- 완료 전 `test`와 `spotlessCheck` 통과를 확인한다.
- `spotlessCheck`가 실패하면 `spotlessApply`를 실행한 뒤 `test`와 `spotlessCheck`를 다시 확인한다.
- 확인하지 않은 검증을 완료로 표시하지 않는다.
