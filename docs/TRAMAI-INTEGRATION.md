# TramAI Integration

How this conference repository consumes TramAI, and why it is wired the way
it is.

## Dependency strategy: pinned submodule + Gradle composite build

The demo needs capabilities that only exist on TramAI `master` (the typed
security surface: `ClassifiedDocument`, `ToolSecurityMetadata`,
`ApprovalSuspendedException`, classification-aware provider routing, audit
chain verification, evidence packs). The latest published artifact on Maven
Central (`0.5.0`, 2026-07-18) does **not** contain them — verified by
inspecting the published jars.

Strategy chosen (evaluated in order):

1. **A — published immutable artifacts**: ✗ not viable. `0.5.0` on Maven
   Central predates the typed security surface.
2. **B — pinned submodule + composite build**: ✓ chosen and verified. The
   submodule is pinned to an exact commit and wired via `includeBuild`.
3. C — bootstrap local publication: not needed.

## How it is wired

```
ktconf-2026-typed-ai-boundaries/settings.gradle.kts
    includeBuild("vendor/tramai")

app/build.gradle.kts
    implementation("dev.tramai:tramai-core:0.5.0")        ← substituted by
    implementation("dev.tramai:tramai-engine:0.5.0")          the included
    implementation("dev.tramai:tramai-security:0.5.0")        build at the
    implementation("dev.tramai:tramai-structured:0.5.0")      pinned commit
    implementation("dev.tramai:tramai-sovereign:0.5.0")
```

The version in the coordinates matches the version the pinned TramAI build
declares for itself (`tramaiVersion=0.5.0` in its `gradle.properties`); the
composite build substitutes the included projects. The `gradle.properties`
field `tramaiGitCommit` records the authoritative pinned SHA.

## Immutability

`vendor/tramai` is read-only. Before and after any work:

```bash
git -C vendor/tramai status --short    # must be empty
git submodule status                   # SHA must match gradle.properties
```

`./scripts/preflight` enforces both. If the submodule is accidentally
modified, revert the changes — never commit inside it.

## Updating the pinned revision (intentional, documented)

1. `git -C vendor/tramai fetch origin && git -C vendor/tramai checkout <NEW-SHA>`
2. Update `tramaiGitCommit` in `gradle.properties` (and `tramaiVersion` if it changed)
3. Run the full deterministic test suite: `./scripts/preflight`
4. Run the rehearsal: `./scripts/rehearse`
5. Update this doc + README (the "Pinned TramAI revision" table)
6. Commit the dependency revision change as its own commit

Never silently follow upstream `master`.

## Offline story

- Preparation time (`./scripts/preflight`): needs network to fetch Gradle
  distribution, plugins, and dependencies once; the submodule is cloned
  with `git clone --recursive`.
- Stage time (`./scripts/demo all`): everything is cached locally
  (Gradle user home + installed distribution + built binary). The demo
  binary itself performs zero network I/O.

## Known-good revision

KTConf validated TramAI revision:
`1ce840fac7a6319e6f1ab8f9a005f92cd2acd691` (master, PR #262 merge — Kotlin enum structured-output schema fix).
