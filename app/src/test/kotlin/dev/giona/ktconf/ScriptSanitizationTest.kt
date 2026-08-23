package dev.giona.ktconf

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The deterministic stage scripts must sanitize BOTH real-provider
 * families (local z840/Qwen AND cloud DeepSeek). A key accidentally
 * present in the operator's shell must never reach the stage process.
 */
class ScriptSanitizationTest {

    private val scripts = listOf("preflight", "rehearse", "stress-rehearse", "stage-up")

    private val requiredTokens = listOf(
        "KTCONF_DEMO_LOCAL_BASE_URL",
        "KTCONF_DEMO_LOCAL_MODEL",
        "KTCONF_DEMO_LOCAL_API_KEY",
        "KTCONF_DEMO_CLOUD_BASE_URL",
        "KTCONF_DEMO_CLOUD_MODEL",
        "KTCONF_DEMO_CLOUD_API_KEY",
    )

    @Test
    fun `deterministic scripts sanitize both provider families`() {
        for (script in scripts) {
            val content = Files.readString(Path.of("..", "scripts", script))
            for (token in requiredTokens) {
                assertTrue(
                    content.contains(token),
                    "$script must sanitize $token (real-provider env must never reach the deterministic stage)",
                )
            }
        }
    }
}
