---
name: release-deurim
description: Cut a Deurim fork release (vX.Y.Z tag on the deurim branch) — pick the version, write user-friendly Korean release notes, tag, push, and watch the release workflow. Use when the user asks to release, cut a version, or write release notes for the fork.
---

# Deurim fork release

Fork-only GitHub Release on `hdream0322/Anki-Android`. Pushing a `v*` tag triggers
`.github/workflows/release-deurim.yml`, which builds the signed universal APK and
uses `release-notes/<tag>.md` verbatim as the release body.

## 1. Survey

```sh
git fetch origin --tags
git tag --sort=-creatordate | grep -E '^v0\.' | head -3   # latest fork tag
git log --oneline --first-parent <last-tag>..HEAD         # fork commits + upstream merges
git log --no-merges --format=%s <last-tag>..HEAD | grep -E '^(fix|feat)'
```

- Ignore inherited upstream tags (`v2.*`, `v0.3`–`v0.7`); fork tags are `v0.X.Y`.
- Working tree must be clean and `deurim` pushed.

## 2. Version

- Default: bump the patch (`v0.1.1` → `v0.1.2`). Suggest a minor bump only for a
  headline feature, and confirm with the user.
- `versionCode = upstreamVersionCode + <patch number>` (see `AnkiDroid/build.gradle`).
  Check the new value is **greater** than the previous release's, otherwise
  installed apps will refuse the update:
  `git show <last-tag>:AnkiDroid/build.gradle | grep 'def upstreamVersionCode'`.

## 3. Pre-flight

- If any `res/values/*strings*.xml` changed since the last tag, run
  `./gradlew :AnkiDroid:lintFullRelease` — release builds run `lintVital`, debug builds don't.
- `./gradlew :AnkiDroid:compilePlayDebugKotlin` must pass.

## 4. Release notes — `release-notes/<tag>.md`

Written for **ordinary app users**, not developers. Follow the previous file's shape:

```md
# vX.Y.Z

<한두 문장 요약: 이번 버전에서 사용자가 체감할 변화>

## 새 기능
- **기능 이름**: 무엇을 할 수 있게 됐는지, 어디서 쓰는지.

## 버그 수정
- <사용자가 겪던 증상>을 고쳤어요.

## 업스트림 동기화
- 최신 upstream 변경사항을 반영했어요.
- 번역이 최신화됐어요.
```

Rules:
- Casual polite Korean (`-어요`). Describe what the user **sees or does**, never how it was built.
- Leave out: class/file/attribute names, commit hashes, library or API names,
  crash/exception names, build/CI/test/lint/refactor changes, internal logging.
- Translate jargon into symptoms: "process death" → "다른 앱을 쓰다 돌아왔을 때",
  "WebView renderer crash" → "카드 화면이 하얗게 비거나 멈추던 문제".
- Pick the 5–8 upstream fixes a user would notice; fold the rest into the
  업스트림 동기화 line. Merge near-duplicates into one bullet.
- Omit an empty section rather than writing "없음".
- Show the draft to the user before tagging if they haven't already approved the content.

## 5. Tag and push

Push only when the user explicitly asked for it **in this turn**.

```sh
git add release-notes/<tag>.md
git commit -m "docs(release): add <tag> release notes"
git tag -a <tag> -m "<tag>"
git push origin deurim
git push origin <tag>
```

## 6. Watch the build

```sh
gh run list -R hdream0322/Anki-Android --workflow release-deurim.yml --limit 1
gh run watch -R hdream0322/Anki-Android <run-id> --exit-status
gh release view <tag> -R hdream0322/Anki-Android
```

`-R` is required: the default `gh` repo resolves to upstream. A run takes about 11 minutes.

If the build fails, fix on `deurim`, then move the tag:
`git tag -d <tag> && git tag -a <tag> -m <tag> <sha> && git push --force origin <tag>`,
and cancel the superseded run with `gh run cancel`.
