---
name: sync-upstream
description: Bring the Deurim fork up to date with ankidroid/Anki-Android — fast-forward the `main` mirror and merge `upstream/main` into `deurim`, resolving the fork's recurring conflicts. Use whenever the user asks to sync, update, or pull from upstream, "upstream 최신화", "업스트림 반영", or wants the latest AnkiDroid changes before a release.
---

# Sync from upstream

Remotes: `origin` = `hdream0322/Anki-Android` (the fork), `upstream` = `ankidroid/Anki-Android`.

- `main` is a pure mirror of `upstream/main`. It carries no fork commits.
- `deurim` is the fork branch (also the GitHub default branch). Upstream is brought in
  by **merge**, never rebase: `deurim` already contains many merge commits, and rebasing
  it replays hundreds of commits into conflicts (this is why the old rebase-based
  `sync-upstream.yml` workflow failed every day).

## 1. Pre-flight

```sh
git status --short          # must be empty; stop and ask if not
git fetch upstream main
git fetch origin
git rev-list --count deurim..upstream/main    # 0 → already up to date, stop here
git rev-list --count origin/deurim..deurim    # unpushed local work? mention it
```

Tell the user how many upstream commits are coming in and the date range
(`git log -1 --format=%ad --date=short upstream/main`).

## 2. Mirror `main`

`main` must only ever fast-forward. Check, then update local and remote:

```sh
git merge-base --is-ancestor origin/main upstream/main && echo ff-ok
git branch -f main upstream/main            # skip if main is checked out somewhere
```

If not `ff-ok`, someone committed to `main`: stop and show
`git log --oneline upstream/main..origin/main` instead of overwriting it.
The push happens in step 6, together with `deurim`.

## 3. Merge into `deurim`

```sh
git checkout deurim
git merge upstream/main     # keep git's default message: "Merge remote-tracking branch 'upstream/main' into deurim"
```

No conflicts → go to step 5.

## 4. Resolve conflicts

List them with `git status --short | grep -E '^(UU|AA|DU|UD|AU|UA|DD)'`.
These come up almost every time and have a known answer:

| File | Resolution |
|---|---|
| `.github/workflows/*.yml` that `deurim` deleted (`publish.yml`, `stale.yml`, `screenshot_*.yml`, `compare_apk_size.yml`, …) | Keep it deleted: `git rm <file>`. The fork deletes upstream workflows so they don't run on the fork. |
| `AnkiDroid/build.gradle(.kts)` | Read every fork change first: `git diff $(git merge-base HEAD MERGE_HEAD) HEAD -- AnkiDroid/build.gradle.kts`. Keep **all** of them; take upstream's side only for the `versionCode = <literal>` value / `upstreamVersionName` and lines the fork never touched. Must never revert: `applicationId = "com.deurim.anki"` (lets the fork install beside official AnkiDroid) and the empty `ACRA_URL` (stops fork crash reports reaching AnkiDroid's server). Others include the fork blocks (`forkVersion`, `forkBuild`, `forkReleaseDate`), `this.versionCode = versionCode!! + forkBuild` (keep it off a line starting with `versionCode`, or upstream's `validateVersionCode` fails), the `versionName` fork suffix, `FORK_*` `buildConfigField`s, `app_name` `AnkiDroid.d`, the orange icon colours, and fork-only dependencies (`konfetti`, `okhttp-sse`, `security-crypto`, `mockwebserver`). |
| `gradle/libs.versions.toml` | Union. Keep fork-only entries (e.g. `konfetti`, `androidxSecurityCrypto`), take upstream's version wherever upstream bumped a shared library. |
| `UD` — upstream deleted or moved a file `deurim` modified | Find where it went: `git log --oneline --diff-filter=D -1 MERGE_HEAD -- <file>` and read that commit. Re-apply the fork's change (`git diff $(git merge-base HEAD MERGE_HEAD) HEAD -- <file>`) to the new location, then `git rm <file>`. If upstream replaced it entirely (e.g. XML layout → Compose), tell the user the fork change has no direct home and ask how to carry it over. |

Since Oct 2026 the build file is Kotlin DSL (`build.gradle.kts`); the fork blocks were
ported in `af83ee0e12`.

Also check for **new** upstream workflow files — they don't conflict, so they slip in
silently and may start running on the fork:
`git diff --name-only --diff-filter=A $(git merge-base HEAD MERGE_HEAD) MERGE_HEAD -- .github/workflows/`
(run before committing, while `MERGE_HEAD` exists). Ask the user whether to delete each one.

For any other conflict (Kotlin, layouts, `values*/` strings):

1. Look at both sides: `git log --oneline -3 deurim -- <file>` and
   `git log --oneline -5 MERGE_HEAD -- <file>` show what each side was doing.
2. Resolve so the fork feature keeps working **on top of** upstream's new code —
   adopt upstream's renames/refactors and re-apply the fork's change to them, rather
   than restoring the old upstream code the fork was based on.
3. Show the user each non-trivial resolution (file + one-line explanation) and get an OK
   before committing. Fork features are the user's own work; a wrong guess silently
   deletes them.

Then `git add` the files and `git commit --no-edit`.

If a conflict is too tangled to resolve confidently, `git merge --abort` and report the
files and why — a half-resolved merge is worse than no merge.

## 5. Verify

```sh
./gradlew :AnkiDroid:compilePlayDebugKotlin
```

Must pass — upstream often renames APIs the fork calls, which merges cleanly but fails
to compile. Fix those in a separate commit after the merge
(`fix(deurim): adapt to upstream <change>`), not by amending the merge.

If `res/values/*strings*.xml` or `values-ko/` changed, also run
`./gradlew :AnkiDroid:lintFullRelease`.

## 6. Report and push

Summarise for the user:

- number of upstream commits merged and the date range
- conflicts and how each was resolved
- build result
- notable upstream changes a user would see (scan `git log --no-merges --format=%s ORIG_HEAD..upstream/main | grep -E '^(feat|fix)'`)

Push **only after the user confirms in this turn**:

```sh
git push origin upstream/main:main
git push origin deurim
```

Both pushes are fast-forwards. Never use `--force` here; if a push is rejected, fetch and
find out why instead.
