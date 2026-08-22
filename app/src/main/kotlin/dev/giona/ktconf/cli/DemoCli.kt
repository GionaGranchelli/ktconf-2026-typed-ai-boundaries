package dev.giona.ktconf.cli

import dev.giona.ktconf.presentation.DemoPresenter
import dev.giona.ktconf.scenarios.ApprovalDecision
import dev.giona.ktconf.scenarios.ApprovalScenario
import dev.giona.ktconf.scenarios.EvidenceScenario
import dev.giona.ktconf.scenarios.InvalidOutputScenario
import dev.giona.ktconf.scenarios.RestrictedDataScenario
import dev.giona.ktconf.scenarios.TypedBoundaryScenario
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/**
 * Stage API. Commands are stable: `./scripts/demo <command>`.
 *
 * The authoritative preparation check is `./scripts/preflight` (shell).
 * This CLI deliberately has no preflight command — one truth, not two.
 */
class DemoCli {

    fun run(args: Array<String>) {
        when (val command = args.firstOrNull() ?: "help") {
            "typed" -> runBlocking { DemoPresenter.typed(TypedBoundaryScenario().run()) }
            "invalid" -> runBlocking { DemoPresenter.invalid(InvalidOutputScenario().run()) }
            "restricted" -> runBlocking { DemoPresenter.restricted(RestrictedDataScenario().run()) }
            "approval" -> runBlocking { DemoPresenter.approval(ApprovalScenario().run(readApprovalDecision())) }
            "evidence" -> runBlocking { DemoPresenter.evidence(EvidenceScenario().run()) }
            "all" -> runAll()
            "reset" -> reset()
            else -> usage()
        }
    }

    private fun runAll() = runBlocking {
        DemoPresenter.typed(TypedBoundaryScenario().run())
        DemoPresenter.invalid(InvalidOutputScenario().run())
        DemoPresenter.restricted(RestrictedDataScenario().run())
        DemoPresenter.approval(ApprovalScenario().run(ApprovalDecision.APPROVE))
        DemoPresenter.evidence(EvidenceScenario().run())
        println()
        println("ALL SCENARIOS PASSED — deterministic demo complete.")
    }

    private fun reset() {
        val dir = Path.of(".build", "demo")
        if (Files.exists(dir)) {
            Files.walk(dir).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
        println("reset: cleared $dir")
    }

    private fun usage() {
        println(
            """
            KTConf 2026 — Typed AI Boundaries (deterministic demo)

            usage: ./scripts/demo <command>

              typed       scenario 1: typed boundary (valid structured output)
              invalid     scenario 2: broken model output rejected by engine
              restricted  scenario 3: RESTRICTED data denied on cloud, LOCAL ok
              approval    scenario 4/5: approve [a] / deny [d] / abort [q]
              evidence    scenario 6: real audit records + verified chain
              all         run every scenario (approval auto-approves)
              reset       clear .build/demo artifacts

            preparation check: ./scripts/preflight  (authoritative)
            """.trimIndent(),
        )
    }

    /**
     * Fail-closed interactive decision: an unknown key never authorizes.
     * Invalid input reprompts; EOF (non-interactive) aborts the workflow.
     */
    private fun readApprovalDecision(): ApprovalDecision {
        while (true) {
            print("[a]pprove / [d]eny / [q]uit: ")
            when (readLine()?.trim()?.lowercase()) {
                "a" -> return ApprovalDecision.APPROVE
                "d" -> return ApprovalDecision.DENY
                "q" -> return ApprovalDecision.ABORT
                null -> {
                    println("(no input — aborting workflow)")
                    return ApprovalDecision.ABORT
                }
                else -> println("unknown input — [a], [d] or [q]")
            }
        }
    }
}
