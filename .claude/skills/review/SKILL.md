---
name: review
description: "코드 리뷰 요청 시 코드 컨벤션과 백엔드 설계 품질을 기준으로 리뷰한다. 코드는 직접 수정하지 않는다. 트리거: '리뷰해줘', '코드 리뷰', '백엔드적으로 봐줘', '컨벤션 봐줘'."
---

## 역할

요청된 코드 또는 현재 브랜치 변경사항을 컨벤션·백엔드 설계 품질 중심으로 리뷰한다.
코드 수정, 리팩토링 패치 작성, 요청하지 않은 기능 추가는 하지 않는다.

## 기본 규칙

- 코드를 직접 수정하지 않는다. 전체 구현 코드를 제공하지 않는다.
- 문제 위치, 영향, 개선 방향, 우선순위를 명확히 적는다.
- 필요할 때만 짧은 예시 코드를 든다.
- 테스트가 필요한 항목은 별도로 제안한다.

## 확인 절차

```shell
git status --short
git diff --stat
git diff --name-only
```

전체 diff는 통계를 본 뒤 필요한 파일 범위로 좁혀서 확인한다.
컨벤션 기준은 `docs/CONVENTIONS.md`를 참고한다.

## 리뷰 순서

위험도 높은 문제 → 백엔드 정합성 → 컨벤션 문제 → 선택적 개선점.

## 결과 형식

- Findings (위치 / 문제 / 영향 / 제안)
- Open Questions 또는 Assumptions
- 짧은 Summary
- Final Verdict: `Approve` / `Approve with Comments` / `Request Changes` / `Blocked`

직설적으로 쓰되 비난하지 않는다.

## 과한 설계 제한

판단 기준은 `docs/CONVENTIONS.md`의 "과한 설계 제한" 섹션을 단일 소스로 따른다. (여기 중복 기재하지 않음)
