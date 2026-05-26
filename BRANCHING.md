# Branching Strategy

This document defines how branches and tags are organized in the Umaboot
repository, how work flows from idea to released artifact, and the merge /
versioning rules everyone follows. Pair it with [`CI.md`](CI.md), which
describes which automation runs against each branch type.

The model is **trunk-based with short-lived branches and tagged releases** —
deliberately lighter than GitFlow because Umaboot has one mainline of work and
a small number of contributors. The full GitFlow ceremony (`develop`, long-lived
`release/*`, `hotfix/*`) is overkill here.

> **Today's repo state:** the default branch is `master` with no remote. The
> first action we'll take after adopting this doc is rename `master` → `main`
> on the local clone (`git branch -m master main`) and on the remote when you
> push it. The rest of the doc assumes `main`.

---

## 1. Branches

| Branch | Purpose | Lifetime | Source | Target |
|---|---|---|---|---|
| `main` | The integration trunk. Always green, always releasable. | Permanent | — | — |
| `feature/<short-name>` | New functionality (panel UI, new generator option, new template, etc.). | Days–weeks | `main` | `main` via PR |
| `fix/<short-name>` | Bug fixes that aren't tied to a specific feature branch. | Hours–days | `main` | `main` via PR |
| `docs/<short-name>` | Doc-only edits (README, USAGE, INTEGRATION_TESTING, this file, etc.). | Hours | `main` | `main` via PR |
| `chore/<short-name>` | Refactors, dependency bumps, build/CI changes, no behavior change. | Hours–days | `main` | `main` via PR |
| `release/v<X.Y.Z>` | Stabilization branch for a specific release. **Optional** — only used when we need to backport fixes after the tag. | Until next release | `main` (at the tag commit) | Cherry-picks back to `main` |

### What each prefix actually means

- **`feature/`** — adds something users can perceive: a new config option, a new
  template, a new IntelliJ panel section, a new CLI flag.
- **`fix/`** — corrects unintended behavior. Includes regression repairs (e.g.
  `fix/mysql-introspect-empty-target`) and minor copy/UX corrections.
- **`docs/`** — README, USAGE, INTEGRATION_TESTING, BRANCHING, CI, code comments
  in isolation. **Excludes** Javadoc updates that go alongside code changes —
  those ride with the relevant `feature/` or `fix/` branch.
- **`chore/`** — pom version bumps, Gradle wrapper updates, IntelliJ Platform
  upgrades, surefire / jacoco config, test-fixture additions, etc.
- **`release/`** — when v0.8.0 is shipped and a critical bug surfaces in v0.8.x
  while v0.9 work is already on `main`, we cut `release/v0.8` from the v0.8.0
  tag, fix the bug there, tag v0.8.1, then cherry-pick the fix forward to
  `main`. Don't create release branches preemptively.

### Branch naming

- All lowercase, kebab-case after the prefix slash.
- Prefix is mandatory: `feature/`, `fix/`, `docs/`, `chore/`, `release/`.
- Aim for ≤40 characters total. The branch name should communicate *what*, not
  *how*. Issue numbers welcome but optional.

Examples:
```
feature/database-dropdown-after-test
feature/cursor-pagination-hex
fix/mysql-public-schema-default
fix/intellij-edt-stackoverflow
docs/integration-testing-v0-8-rows
chore/bump-mybatis-spring-boot-3.0.5
release/v0.8
```

Anti-examples:
```
my-changes              ← no prefix, vague
feature/Fix_Bug_42      ← snake_case, vague
feature/IT-1234         ← issue-number-only — say what it does
```

---

## 2. Merge rules

| Rule | Why |
|---|---|
| **Squash merge** to `main` (one commit per PR). | Keeps `main`'s history linear and bisectable. The PR's commit message becomes the `main` commit message — write it well. |
| **No merge commits** on `main`. | Same reason. |
| **All PRs require a green CI build** before merge. | See `CI.md`. |
| **At least one review** for non-trivial changes. (Solo work on a personal repo? Self-merge is fine but write a real PR description.) | Catches the obvious mistakes that the test suite doesn't. |
| **Never push directly to `main`.** | One automation point of entry → one set of CI checks → one place to revert from. |
| **Branches are deleted after merge.** | Reduces clutter. The squashed commit is the historical record. |

### Merge commit message format

Squash-merge commits should be **conventional-commit-style**:

```
<type>(<scope>): <subject>

<body — what changed and why, ~3-5 lines, wrap at ~72 chars>

Closes #<issue> (if applicable)
```

Where `<type>` ∈ `{feat, fix, docs, chore, refactor, test, build, ci}` and
`<scope>` is one of the modules: `core`, `cli`, `intellij`, `vscode`,
`fixtures`, `parent`, or a sub-area like `templates`, `config`, `panel`.

Examples:
```
feat(intellij): move database field into host card so URL mode is self-contained

The Connection group's Database field used to live in the always-visible
shared block. When swapping from host → URL mode the value would carry over
and silently contradict the URL. Move it into the host card so it hides on
URL-mode swap; in URL mode the database is now parsed from the URL only.

Closes #42
```

```
fix(core): MysqlIntrospector silently returns 0 tables when DB doesn't exist

Add requireSchemaExists() check at the start of introspect(). Throws
IllegalArgumentException with a clear message instead of wandering on to
return an empty SchemaModel.
```

We're not enforcing this with a commit-lint hook today — it's a convention to
help future-you read the changelog. If we get to v1.x and start auto-generating
release notes, we'll formalize it then.

---

## 3. Versioning & releases

### Scheme

**Semantic versioning** (`<major>.<minor>.<patch>`):

- **Major** (`1.0.0` → `2.0.0`): backwards-incompatible config change, dropped
  Java/SB combo, removed CLI flag, removed generator option.
- **Minor** (`0.7.0` → `0.8.0`): new feature, new config option (additive),
  new template, new architecture/persistence combo. Backwards-compatible.
- **Patch** (`0.8.0` → `0.8.1`): bug fix, internal refactor, doc update,
  template typo. No user-visible API/config change.

We're pre-1.0 today, which by semver convention means breaking changes are
allowed in minor bumps. Use that latitude for design corrections (the v0.8
connection redesign is a recent example) — but document each breaking change
in the release notes.

### Tags

```
v<X.Y.Z>
```

Tags live on `main` (or on `release/v<X.Y>` for back-ported fixes). The tag
message should summarize the release; CI publishes artifacts based on tag
matches.

### Version alignment across artifacts

The repo has three independent version numbers today, which will get confusing:

| Artifact | Where the version lives | Currently |
|---|---|---|
| `umaboot-parent` (Maven reactor) | `pom.xml` `<version>` | `0.1.0-SNAPSHOT` |
| `umaboot-intellij` plugin | `umaboot-intellij/build.gradle.kts` `version` | `0.7.0-SNAPSHOT` |
| `umaboot-vscode` extension | `umaboot-vscode/package.json` `version` | `0.5.0` |

**Going forward:** all three track the repo tag. When we cut `v0.8.0`:

```
mvn versions:set -DnewVersion=0.8.0           # all four Maven modules
# umaboot-intellij/build.gradle.kts:           version = "0.8.0"
# umaboot-vscode/package.json:                 "version": "0.8.0"
git tag -a v0.8.0 -m "v0.8.0 — connection redesign"
git push origin v0.8.0
```

CI handles the publishing (see `CI.md` § Release workflow).

A small bash/PowerShell helper script (`scripts/set-version.sh` /
`scripts/Set-Version.ps1`) is on the wishlist — it would update all three in
one shot. Not blocking the first release.

---

## 4. Common workflows

### A. Starting a new feature

```bash
git checkout main
git pull
git switch -c feature/short-descriptive-name

# ... work, commit, work, commit ...

git push -u origin feature/short-descriptive-name
# Open a PR against main on the host (GitHub/GitLab).
# Wait for CI green. Address review. Squash-merge.
```

### B. Fixing a bug found in `main`

Same as A but use `fix/` prefix and reference the issue (if any) in the PR
body. CI gating is identical.

### C. Cutting a release

1. On `main`, bump versions in all three places (see § Version alignment).
2. Update CHANGELOG.md (when we have one — for now, the GitHub release notes
   are the changelog).
3. Open a PR titled `chore: release v0.8.0`. Get it green and merge.
4. From the resulting `main` commit, tag:
   ```bash
   git checkout main && git pull
   git tag -a v0.8.0 -m "v0.8.0"
   git push origin v0.8.0
   ```
5. The tag triggers the release workflow. See `CI.md` § Release workflow for
   the artifact list.

### D. Hot-fixing a released version while `main` is already ahead

Only when something genuinely urgent is broken in a shipped version. If `main`
has moved on but a bug was found in `v0.8.0`:

```bash
git checkout v0.8.0
git switch -c release/v0.8
git switch -c fix/critical-thing
# fix, commit
git push -u origin fix/critical-thing
# PR against release/v0.8 (NOT main).
# After merge, on release/v0.8:
mvn versions:set -DnewVersion=0.8.1
# (also update intellij + vscode)
git commit -am "chore: release v0.8.1"
git tag -a v0.8.1 -m "v0.8.1 — fix critical-thing"
git push origin release/v0.8 v0.8.1
# Cherry-pick the same fix forward to main if applicable:
git checkout main
git cherry-pick <fix-commit-sha>
git push
```

### E. Long-running experimental work

If a feature will take more than ~2 weeks and risks getting stale on a feature
branch, **rebase frequently** rather than letting it diverge:

```bash
git checkout main && git pull
git checkout feature/long-thing
git rebase main
# Resolve conflicts, push --force-with-lease.
```

Avoid `git merge main` into a feature branch — it makes the eventual squash
diff harder to read.

---

## 5. Things we explicitly are NOT doing

These are intentional simplifications. If the project grows or the team grows,
revisit them.

- **No `develop` branch.** All integration happens on `main`.
- **No long-lived `release/*` branches by default.** Created on demand for
  back-ports only (workflow D above).
- **No protected commit-lint hook today.** Conventions are honor-system.
- **No required signed commits.** Add when we have signing keys to manage.
- **No automated semantic-version bumping.** Versions are bumped by humans in
  the release PR — we want a human to read the diff and decide major/minor/patch.
- **No `next-release` floating tag.** The latest tag is the latest release.

---

## 6. FAQ

**Q: Why squash and not rebase merge?**
Squash gives one tidy commit on `main` per PR — the PR description becomes
the commit message, which is what we want for `git log`. Rebase merge
preserves the in-flight commits, which is too noisy for a project this size.

**Q: Can I push directly to `main` for a one-line typo fix?**
No. The PR overhead for a typo is real but the consistency win is bigger
(every change goes through the same gate). Make a `docs/typo-readme` branch
and self-merge — total time ~30 seconds.

**Q: What about formatting-only commits?**
Roll them into the PR they belong to. Don't make a separate `chore/format`
branch unless you're applying a project-wide format change (e.g. switching to
Spotless).

**Q: Should I rebase before squash-merge?**
The host's "squash merge" button does it for you. If you want to confirm the
diff is clean first, rebase locally, push --force-with-lease, then merge.

**Q: I want to share my work-in-progress with a collaborator without a PR yet.**
Push the branch to the remote — branches don't require PRs to exist. Open
the PR as a draft when you're ready for feedback.
