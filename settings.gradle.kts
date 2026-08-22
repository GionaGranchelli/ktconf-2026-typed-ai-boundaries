rootProject.name = "ktconf-2026-typed-ai-boundaries"

include("app")

// TramAI is consumed as an immutable external dependency:
// a pinned Git submodule under vendor/tramai, wired in as a Gradle
// composite build. See docs/TRAMAI-INTEGRATION.md.
includeBuild("vendor/tramai")
