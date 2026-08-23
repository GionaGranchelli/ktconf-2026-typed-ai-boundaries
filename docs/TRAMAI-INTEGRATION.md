# TramAI Integration

How this conference repository consumes TramAI, and why it is wired the way
it is.

## Dependency strategy: pinned submodule + Gradle composite build

The demo needs capabilities that only exist on TramAI `master` (the typed
security surface: `ClassifiedDocument`, `ToolSecurityMetadata`,
`ApprovalSuspendedException`, classification-aware provider policy
enforcement, audit chain verification, evidence packs, and the sovereign
Spring Boot starter). When this demo was frozen, the published `0.5.0`
artifacts (2026-07-18) did **not** contain them. The conference artifact
remains pinned to a validated source revision for reproducibility, even if
newer releases exist.

Strategy chosen (evaluated in order):

1. **A — published immutable artifacts**: ✗ not viable. `0.5.0` on Maven
   Central predates the typed security surface and the starter.
2. **B — pinned submodule + composite build**: ✓ chosen and verified. The
   submodule is pinned to an exact commit and wired via `includeBuild`.
3. C — bootstrap local publication: not needed.

## How it is wired

```
ktconf-2026-typed-ai-boundaries/settings.gradle.kts
    includeBuild("vendor/tramai")

app/build.gradle.kts
    implementation("dev.tramai:tramai-spring-boot-starter-sovereign:0.5.0")
    implementation("dev.tramai:tramai-openai:0.5.0")   (optional real-model adapter)
```

The starter (`tramai-spring-boot-starter-sovereign`) api-exposes the
sovereign, security and core modules and auto-configures ALL default
sovereign infrastructure: model registry (from `tramai.sovereign.models`),
in-memory audit/approval/continuation stores, approval gate coordinator,
token generator/digesters, `SovereignTramai` and `SovereignTramaiRuntime`.
It collects `ModelProvider` beans and — since upstream tramAI PR #268 —
`TramaiTool` Spring beans from the application context. The version in the
coordinates matches the version the pinned TramAI build declares for itself
(`tramaiVersion=0.5.0`); the composite build substitutes the included
projects. `gradle.properties` field `tramaiGitCommit` records the
authoritative pinned SHA — currently **9b56530c549ef80c0aa9f4ffb034abc1cf5d769d**
(the merge of PR #268, sovereign-starter tool collection).

## Immutability

`vendor/tramai` is read-only. Before and after any work:

```bash
git -C vendor/tramai status --short    # must be empty
git -C vendor/tramai rev-parse HEAD    # must equal tramaiGitCommit
```

## Offline story

- Preparation time (`./scripts/preflight`): needs network to fetch Gradle
  distribution, plugins, and dependencies once; the submodule is cloned
  with `git clone --recursive`.
- Stage time (`./scripts/stage-up` + `./scripts/demo`): everything is
  cached locally (Gradle user home + built bootJar). The application
  itself performs zero network I/O in the deterministic instances.

## Known-good revision

The pinned revision is the known-good combination:
`tramaiGitCommit=9b56530c549ef80c0aa9f4ffb034abc1cf5d769d`
(PR #268 — sovereign starter collects `TramaiTool` beans — on top of the
enum-schema fix from #261/#262 and the structured-output TCK from #266).
Optional real-provider path verified against Qwen3.8-27B-UD-Q6_K on the
z840 (Tailscale, LOCAL) and DeepSeek V4 Flash (GLOBAL_CLOUD). The freeze
tag `ktconf-2026-demo-v4` records this known-good combination;
`ktconf-2026-demo-v3` records the previous four-profile combination
(fallback only).

## How to repin (documented procedure — never silently)

1. Create the upstream TramAI change as a PR, review it, merge it.
2. `git -C vendor/tramai fetch && git -C vendor/tramai checkout <merge-sha>`
3. Update `gradle.properties`: `tramaiGitCommit=<merge-sha>`
4. `git add vendor/tramai gradle.properties && git commit`
5. `./scripts/preflight` + `./scripts/stress-rehearse` + live stage smoke.
6. Re-tag the freeze point.
