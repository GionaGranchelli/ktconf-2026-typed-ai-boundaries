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
 */
class DemoCli {

    fun run(args: Array<String>) {
        when (val command = args.firstOrNull() ?: "help") {
            "preflight" -> preflight()
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

    private fun preflight() {
        println("preflight: deterministic demo (offline-capable after preparation)")
        println("tramai pinned = ${tramaiPinnedCommit()}")
        println("submodule     = ${submoduleCommit()}")
        println("match         = ${tramaiPinnedCommit() == submoduleCommit()}")
        println("JDK           = ${System.getProperty("java.version")}")
        println("cwd           = ${Path.of("").toAbsolutePath().normalize()}")
    }

    private fun reset() {
        val dir = Path.of(".build", "demo")
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
        println("reset: cleared $dir")
    }

    private fun usage() {
        println(
            """
            KTConf 2026 — Typed AI Boundaries (deterministic demo)

            usage: ./scripts/demo <command>

              preflight   verify pinned TramAI revision + environment
              typed       scenario 1: typed boundary (valid structured output)
              invalid     scenario 2: broken model output rejected by engine
              restricted  scenario 3: RESTRICTED data denied on cloud, LOCAL ok
              approval    scenario 4/5: approve [a] / deny [d] / abort [q]
              evidence    scenario 6: real audit records + verified chain
              all         run every scenario (approval auto-approves)
              reset       clear .build/demo artifacts
            """.trimIndent(),
        )
    }

    private fun readApprovalDecision(): ApprovalDecision {
        print("[a]pprove / [d]eny / [q]uit: ")
        return when (readLine()?.trim()?.lowercase()) {
            "a" -> ApprovalDecision.APPROVE
            "d" -> ApprovalDecision.DENY
            "q", null -> ApprovalDecision.ABORT
            else -> {
                println("(defaulting to approve)")
                ApprovalDecision.APPROVE
            }
        }
    }

    private fun tramaiPinnedCommit(): String {
        val properties = Path.of("gradle.properties")
        return if (Files.exists(properties)) {
            Files.readAllLines(properties)
                .firstOrNull { it.startsWith("tramaiGitCommit=") }
                ?.substringAfter("=") ?: "UNKNOWN"
        } else {
            "UNKNOWN"
        }
    }

    private fun submoduleCommit(): String {
        val head = Path.of("vendor", "tramai", ".git", "HEAD")
        return if (Files.exists(head)) {
            Files.readAllLines(head).firstOrNull()?.trim()?.substringAfterLast(" ") ?: "UNKNOWN"
        } else {
            "UNKNOWN"
        }
    }
}
