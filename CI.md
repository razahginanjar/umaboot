# CI Pipeline

This document describes the continuous-integration setup for the Umaboot
repository: which jobs run on which events, what gates a pull request, and how
released artifacts are produced. Pair it with [`BRANCHING.md`](BRANCHING.md),
which defines the branch model the CI is built around.

The pipeline is currently designed for **GitHub Actions**. GitLab CI
equivalents are noted at the end of each section — the project is host-agnostic
and the generator itself emits both styles for user projects, but we have to
pick one for our own repo. Switch by porting the YAML.

> **Today's repo state:** there are no workflow files in the repo yet. This doc
> is the design; the actual `.github/workflows/*.yml` files come in a follow-up
> commit (see § Implementation checklist at the end).

---

## 1. What this CI must do

Tied to the project's structure, the pipeline has six distinct jobs spread
across three workflows. The job table is the reference; the workflow files
below assemble these jobs onto the right events.

| # | Job | What it builds / runs | Roughly how long | Gating |
|---|---|---|---|---|
| 1 | **`build-core`** | `mvn -pl umaboot-core,umaboot-cli,umaboot-test-fixtures,umaboot-parent install -DskipITs -Dtest='!*IntegrationTest'` | 1–2 min | **Gates PRs** |
| 2 | **`unit-tests`** | Same as `build-core`; surfaces test reports as job output. (Could merge with #1 — kept separate for clearer failures.) | 1–2 min | **Gates PRs** |
| 3 | **`integration-tests`** | `mvn -pl umaboot-core test -Dtest='*IntegrationTest'` (Testcontainers — Postgres + MySQL). Requires Docker on the runner. | 3–5 min | **Gates merges to `main` and tag pushes**. Optional on PRs (see policy below). |
| 4 | **`build-intellij`** | `cd umaboot-intellij && gradle :buildPlugin`. Requires the IntelliJ Platform deps cache and Java 21. Depends on `build-core` having installed `umaboot-core` to `~/.m2`. | 2–4 min | **Gates PRs** |
| 5 | **`build-vscode`** | `cd umaboot-vscode && npm ci && npm run compile && npx @vscode/vsce package`. | 30 s | **Gates PRs** |
| 6 | **`publish-release`** | Uploads the three artifacts (`umaboot-cli/target/umaboot.jar`, `umaboot-intellij/build/distributions/umaboot-intellij-*.zip`, `umaboot-vscode/umaboot-*.vsix`) to a GitHub Release. Optionally publishes to JetBrains Marketplace + VS Code Marketplace + Maven Central if secrets are configured. | 1–2 min | Runs only on `v*` tag pushes |

### PR integration-test policy

Testcontainers tests are slow and need Docker. On runners that have Docker
(GitHub Actions Linux runners do; Windows / macOS don't, by default), running
them on every PR is feasible. Two options:

- **A. Strict** — `integration-tests` gates every PR. Total PR feedback time
  ~5 min. Use this once the suite is stable.
- **B. Lenient** — `integration-tests` runs only on `main` pushes and tag
  pushes. PRs gate on jobs 1–2 + 4 + 5. Saves ~3 min per PR but lets a
  flaky-introspection commit slip into `main` until the post-merge run catches
  it.

**Today's policy:** B. We have a single existing integration test
(`GenerateIntegrationTest`) and the new `rejectsNonExistentSchema` case. As
the suite grows (per-condition fixtures from the `fixtures/postgres/` and
`fixtures/mysql/` set), we'll switch to A.

---

## 2. Workflows

Three workflow files live under `.github/workflows/`. They are designed to be
**read together** — each one is short because shared steps live in
`.github/actions/setup-jvm/action.yml` (composite action) — see § Reusable
setup below.

### 2.1 `pr.yml` — runs on every pull request

**Trigger:** `pull_request` against `main`.

**Jobs:**

```yaml
# .github/workflows/pr.yml
name: PR

on:
  pull_request:
    branches: [main]

concurrency:
  # Cancel in-progress runs of the same PR when a new commit is pushed.
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  build-core:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-jvm
        with: { java-version: '17' }
      - name: Build core / cli / fixtures (no integration tests)
        run: |
          mvn -B install \
            -DskipITs \
            "-Dtest=!*IntegrationTest" \
            "-Dsurefire.failIfNoSpecifiedTests=false"
      - name: Upload surefire reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports-pr
          path: '**/target/surefire-reports/'

  build-intellij:
    runs-on: ubuntu-latest
    needs: build-core
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-jvm
        with: { java-version: '21' }
      - name: Restore maven cache  # build-intellij depends on umaboot-core in ~/.m2
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      - name: Re-install umaboot-core into ~/.m2
        run: mvn -B -pl umaboot-core -am install -DskipTests
      - name: Build IntelliJ plugin
        working-directory: umaboot-intellij
        run: gradle :buildPlugin --no-daemon
      - name: Upload plugin zip
        uses: actions/upload-artifact@v4
        with:
          name: umaboot-intellij-plugin
          path: umaboot-intellij/build/distributions/*.zip

  build-vscode:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: 'npm', cache-dependency-path: 'umaboot-vscode/package-lock.json' }
      - name: npm ci + compile + vsix
        working-directory: umaboot-vscode
        run: |
          npm ci
          npm run compile
          npx @vscode/vsce package
      - name: Upload vsix
        uses: actions/upload-artifact@v4
        with:
          name: umaboot-vscode-extension
          path: umaboot-vscode/*.vsix
```

This gives ~5–7 minute feedback on every PR with three artifacts uploaded for
manual smoke-testing. Failures expose `target/surefire-reports/` for triage.

### 2.2 `main.yml` — runs on every push to `main`

**Trigger:** `push` to `main` (post-merge).

**Jobs:** all of the PR jobs **plus** the integration-test suite. The
post-merge run is the one that gates whether `main` is releasable — if it
fails, revert the merge or push a fix.

```yaml
# .github/workflows/main.yml
name: Main

on:
  push:
    branches: [main]

jobs:
  build-core:
    # … same as pr.yml build-core job …

  integration-tests:
    runs-on: ubuntu-latest
    needs: build-core
    steps:
      - uses: actions/checkout@v4
      - uses: ./.github/actions/setup-jvm
        with: { java-version: '17' }
      - name: Run *IntegrationTest only (Testcontainers)
        run: mvn -B -pl umaboot-core test "-Dtest=*IntegrationTest"
      - name: Upload surefire reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports-main
          path: '**/target/surefire-reports/'

  build-intellij:
    # … same as pr.yml build-intellij job …

  build-vscode:
    # … same as pr.yml build-vscode job …
```

> Docker is available on `ubuntu-latest` GitHub-hosted runners by default.
> Testcontainers picks it up automatically.

### 2.3 `release.yml` — runs on tag pushes matching `v*`

**Trigger:** `push` to a tag matching `v*` (e.g. `v0.8.0`, `v1.0.0`).

**Jobs:** the same build set as `main.yml` (so the artifacts are rebuilt from
the tagged commit, not relied upon from a previous run), then a publish job:

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags: ['v*']

permissions:
  contents: write   # required to create the GitHub Release

jobs:
  build-and-test:
    # … reuses the workflow_call shape of main.yml; produces the three
    # artifacts as workflow outputs / uploaded artifacts …

  publish:
    needs: build-and-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with: { path: artifacts }
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          name: ${{ github.ref_name }}
          files: |
            artifacts/umaboot-cli-fat-jar/umaboot.jar
            artifacts/umaboot-intellij-plugin/*.zip
            artifacts/umaboot-vscode-extension/*.vsix
          generate_release_notes: true
```

Optional publish targets, gated on having the relevant secret:

| Target | Secret | When to enable |
|---|---|---|
| **JetBrains Marketplace** | `JETBRAINS_MARKETPLACE_TOKEN` | Once you've claimed the plugin on `plugins.jetbrains.com` |
| **VS Code Marketplace** | `VSCE_PAT` | Once you've created a Marketplace publisher and PAT |
| **Maven Central** (umaboot-core, umaboot-cli) | `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `MAVEN_GPG_*` | Only when you want others to depend on `umaboot-core` as a library |

For the first release, GitHub Releases alone is enough — users download the
JAR / zip / vsix and install manually.

---

## 3. Reusable setup

To avoid repeating JDK + cache configuration in every job, factor it into a
composite action:

```yaml
# .github/actions/setup-jvm/action.yml
name: Setup JVM
description: Install Temurin + cache Maven and Gradle
inputs:
  java-version:
    required: true
runs:
  using: composite
  steps:
    - uses: actions/setup-java@v4
      with:
        distribution: temurin
        java-version: ${{ inputs.java-version }}
        cache: maven
    - name: Cache Gradle
      uses: actions/cache@v4
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
          umaboot-intellij/.gradle
        key: ${{ runner.os }}-gradle-${{ hashFiles('umaboot-intellij/**/*.gradle*') }}
        restore-keys: ${{ runner.os }}-gradle-
    - name: Cache Foojay JDK list  # used by umaboot-intellij toolchain resolver
      uses: actions/cache@v4
      with:
        path: ~/.gradle/jdks
        key: foojay-jdks-21
```

The Foojay cache is worth the extra step — first-time `gradle :buildPlugin`
downloads ~200 MB of JDK 21 to satisfy the IntelliJ Platform 2024.2.4
toolchain. With this cache the second run is ~1 min faster.

---

## 4. Job concurrency & matrix

Today everything runs single-threaded on Linux. Two extensions to consider
later, both on the wishlist not the critical path:

- **OS matrix** — adding `windows-latest` to `build-intellij` would catch
  path-separator and CRLF bugs early. Cost: extra ~3 min per PR. Recommended
  once we have a contributor on Windows actively pushing PRs.
- **DB engine matrix** for `integration-tests` — currently the
  `GenerateIntegrationTest` only spins up Postgres. The new fixtures under
  `umaboot-test-fixtures/src/main/resources/fixtures/mysql/` justify a MySQL
  matrix entry once we wire integration tests against them.

---

## 5. Caching

| Cache | Path | Key | Invalidated when |
|---|---|---|---|
| Maven local repo | `~/.m2/repository` | `os + hashFiles('**/pom.xml')` | Any pom changes |
| Gradle | `~/.gradle/caches`, `umaboot-intellij/.gradle` | `os + hashFiles('umaboot-intellij/**/*.gradle*')` | build.gradle.kts changes |
| Foojay JDK | `~/.gradle/jdks` | `foojay-jdks-21` | Manual bust (rare) |
| npm | `umaboot-vscode/node_modules` (via `cache: npm` on `setup-node`) | `package-lock.json` | Lockfile changes |

Cold cache: ~10 min for a full PR run. Warm cache: ~5 min.

---

## 6. Failure handling

- **Surefire reports** are uploaded as artifacts on test failure, retained for
  90 days. Click into the failed job → "Artifacts" → download the zip.
- **Logs** stay attached to the run for 90 days (default GH Actions retention).
- **Re-running** a failed job: use the GH UI re-run button. If the failure is
  flaky (network, Docker-not-ready), re-run only the failed job; if the
  failure is a real test issue, push a `fix/` branch instead.

---

## 7. Manual / out-of-band work

Some tasks intentionally don't run in CI today:

| Task | Why | How to do it |
|---|---|---|
| Manual integration testing per `INTEGRATION_TESTING.md` | UI-driven IntelliJ + VS Code panel verification can't be automated cheaply | Follow the `I-` and `V-` checklists locally before merging UI-changing PRs |
| JetBrains Marketplace verification | The marketplace's own validator is more thorough than ours; run it once before publishing | `gradle :verifyPlugin` locally |
| `mvn dependency:analyze` | Surfaces unused or undeclared deps | Run quarterly; fix what's worth fixing |
| Security scanning | No SAST configured yet | When the project gets traction, enable GitHub CodeQL or equivalent |
| License audit | No license-compatibility check yet | When the project gets traction, enable an action like `actions/dependency-review-action` |

---

## 8. Implementation checklist

When you're ready to actually wire this up:

- [ ] Rename `master` → `main` locally (`git branch -m master main`) and on the
      remote (`git push -u origin main` then change default branch in repo
      settings + delete `origin/master`).
- [ ] Add `.github/actions/setup-jvm/action.yml` (the composite action).
- [ ] Add `.github/workflows/pr.yml` with the three build jobs.
- [ ] Add `.github/workflows/main.yml` with the same three + integration-tests.
- [ ] Add `.github/workflows/release.yml` with the publish job.
- [ ] (Optional) Add a status badge to `README.md`:
      `![CI](https://github.com/<owner>/<repo>/actions/workflows/main.yml/badge.svg)`
- [ ] (Optional) Configure branch protection on `main`: require status checks
      `build-core`, `build-intellij`, `build-vscode` to pass; require linear
      history; disallow direct pushes.
- [ ] (Optional) Configure branch protection on `release/v*`: same as `main`.
- [ ] (Optional) Set up the publish secrets (`JETBRAINS_MARKETPLACE_TOKEN`,
      `VSCE_PAT`, `OSSRH_*`) when ready to publish to the respective
      marketplaces.

---

## 9. GitLab CI equivalents

If you eventually move to GitLab, the same six jobs map to a single
`.gitlab-ci.yml` with three stages: `build`, `test`, `release`. The mappings:

| GitHub Actions concept | GitLab CI equivalent |
|---|---|
| `on: pull_request` | `rules: - if: $CI_PIPELINE_SOURCE == 'merge_request_event'` |
| `on: push: branches: [main]` | `rules: - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH` |
| `on: push: tags: ['v*']` | `rules: - if: $CI_COMMIT_TAG =~ /^v/` |
| `concurrency: cancel-in-progress: true` | `interruptible: true` |
| `actions/cache@v4` | `cache: { key, paths }` per job |
| `actions/upload-artifact@v4` | `artifacts: { paths, expire_in }` per job |
| `actions/setup-java@v4` | Use a Temurin image (`eclipse-temurin:17-jdk`) as `image:` |
| `softprops/action-gh-release` | GitLab Release API via `release:` keyword |

The CLI commands inside each job stay byte-for-byte identical — only the
trigger / artifact / cache plumbing changes.

---

## 10. FAQ

**Q: Why do `build-core` and `build-intellij` run separately when the plugin
needs core in `~/.m2`?**
The plugin job pulls core from a Maven cache, which the core job warms. They
*could* be one giant job, but splitting them gives clearer failure modes
(core compile error vs plugin build error) and lets the VS Code job run in
parallel with both.

**Q: Why install `umaboot-core` again inside `build-intellij` if the cache is
warm?**
Cache hits are best-effort; on a cold cache or cache key change, we'd build
the plugin against a stale or missing JAR. The re-install is fast (seconds)
and ensures correctness.

**Q: Is `mvn -B install` over the top — wouldn't `mvn package` do?**
We need the artifacts in `~/.m2` for the IntelliJ plugin job (and any future
inter-module test fixture pulls). `install` is the cheapest correct command.

**Q: What about test reports as a UI element?**
GH Actions doesn't render JUnit XML natively. If we want pretty test reports
in PR comments, add `dorny/test-reporter@v1` to the `unit-tests` step.

**Q: Should we publish to Maven Central from day one?**
No. Publishing to Maven Central requires Sonatype OSSRH account + GPG signing
+ namespace verification, all of which are admin-heavy. Until external users
want to depend on `umaboot-core` as a library, GitHub Releases alone is
enough.

**Q: Where do code-coverage numbers go?**
The parent pom already has Jacoco configured. To surface coverage in CI, add
a step `mvn jacoco:report` and upload `target/site/jacoco/` as an artifact —
or push to Codecov / Coveralls. Defer until we have a coverage target to hold
ourselves to.
