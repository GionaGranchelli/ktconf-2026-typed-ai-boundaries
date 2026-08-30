package dev.giona.ktconf

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Architecture guard: the conference application demonstrates the PUBLIC
 * Spring developer surface of TramAI. If production code reintroduces manual
 * construction of default sovereign stores, coordinators, token machinery,
 * or unmanaged runtime extraction, this test fails. Runtime composition is
 * allowed because the observability seam attaches an OperationObserver while
 * reusing infrastructure supplied by the Spring starter.
 */
class ArchitectureGuardTest {

    private val forbidden = listOf(
        "SovereignProfileConfiguration(",
        "DefaultApprovalGateCoordinator(",
        "InMemoryModelRegistry(",
        "InMemoryAuditStore(",
        "InMemoryApprovalStore(",
        "InMemoryApprovalContinuationStore(",
        "ApprovalTokenGenerator(",
        "ApprovalTokenDigester(",
        "ToolArgumentsDigester(",
        ".runtime()",
    )

    @Test
    fun `production code never manually constructs default sovereign infrastructure`() {
        // Gradle runs tests with the module directory (app/) as working dir.
        val srcMain = Path.of("src", "main", "kotlin")
        assertTrue(Files.isDirectory(srcMain), "guard must run from the app module directory (cwd=${System.getProperty("user.dir")})")
        val violations = mutableListOf<String>()

        Files.walk(srcMain)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .forEach { file ->
                val content = Files.readString(file)
                for (token in forbidden) {
                    var idx = content.indexOf(token)
                    while (idx != -1) {
                        violations += "${relative(file)}:${lineOf(content, idx)}: '$token'"
                        idx = content.indexOf(token, idx + 1)
                    }
                }
            }

        assertTrue(violations.isEmpty(), "Forbidden manual sovereign infrastructure in production code:\n${violations.joinToString("\n")}")
    }

    private fun relative(path: Path): String = path.toString()

    private fun lineOf(content: String, index: Int): Int =
        content.substring(0, index).count { it == '\n' } + 1
}
