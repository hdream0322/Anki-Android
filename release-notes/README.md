# Release notes

각 fork 릴리스마다 `v<MAJOR>.<MINOR>.<PATCH>.md` 파일을 하나씩 둔다.
태그 `vX.Y.Z` 를 push 하면 `.github/workflows/release-deurim.yml` 이
같은 이름의 파일 내용을 GitHub Release body 로 그대로 올리고,
인앱 "새 버전 안내" 다이얼로그도 같은 텍스트를 보여 준다.

해당 파일이 없으면 GitHub auto-generated PR 목록으로 폴백한다.

## 스타일
- 가장 위 한두 줄로 이번 빌드의 메시지를 사람 말로 한 문장.
- `## 새 기능` / `## 다듬은 부분` / `## 알아 둘 것` 같은 소제목 + 짧은 불릿.
- 사용자 입장에서 의미 있는 변화만 적는다. 내부 리팩터/lint 수정은 생략.
- 길이는 한 화면 안에 들어올 정도가 보기 좋다.
