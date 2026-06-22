---
name: pr-writer
description: "PR 작성/PR 올리기/PR 만들기 요청 시 현재 브랜치의 커밋과 변경사항을 분석해 PR 제목·본문을 작성하고, 명확히 요청된 경우에만 GitHub CLI로 PR을 생성한다. 트리거: 'pr 작성', 'pr 올려줘', 'PR 만들어'."
---

## 역할

현재 브랜치의 실제 커밋과 변경사항을 기준으로 PR 제목과 본문을 작성한다.
사용자가 PR 생성까지 명확히 요청한 경우에만 `gh pr create`로 생성한다.
코드 수정, 커밋, push는 하지 않는다.

## 기본 규칙

- 실제 커밋/변경 파일/diff에 근거해서만 작성한다. 추측·향후 계획은 넣지 않는다.
- 확인하지 않은 테스트를 완료로 표시하지 않는다.
- `## AI 활용 내용` 섹션은 사용자가 PR 생성 후 직접 수정하므로 제목 아래 `-`만 남기고 placeholder 문구를 넣지 않는다.
- base 브랜치는 지시 없으면 `origin/develop` → `origin/main` 순.
- 현재 브랜치가 push되어 있지 않으면 생성하지 않고 push 필요를 안내한다.

## 확인 절차

```shell
git branch --show-current
git status --short
git log --oneline -n 10
git diff --stat <base>...HEAD
git diff --name-only <base>...HEAD
```

전체 diff는 파일 목록/통계로 부족할 때만 `git diff <base>...HEAD -- <file>`로 좁혀서 본다.

## PR 제목

태그 하나 사용: `[feat]` `[fix]` `[refactor]` `[docs]` `[test]` `[chore]`
예: `[feat] 1:1 채팅 메시지 전송 기능 구현`

## PR 본문 템플릿

```markdown
## 작업 내용
-

## 변경 이유
-

## 주요 변경 사항
-

## 테스트 및 확인
- [ ]

## AI 활용 내용
-

## 리뷰 포인트
-
```

실행한 OS에 맞는 실제 테스트 명령만 체크한다.
(macOS/Linux `./gradlew test`, Windows `.\gradlew.bat test`)
이슈 연동이 필요하면 본문 마지막에 `Closes #이슈번호`를 넣는다.

## PR 생성

"pr 올려줘", "PR 만들어줘"처럼 생성을 명확히 요청한 경우에만. 여러 줄 명령은 한 줄로.

```shell
gh pr create --base <base> --head <current> --title "<title>" --body "<body>"
```

`gh` 미설치/미인증이거나 미push면 제목·본문만 제공하고 사유를 설명한다.
