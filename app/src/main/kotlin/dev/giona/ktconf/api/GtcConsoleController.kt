package dev.giona.ktconf.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** Serves the embedded Vite SPA at its recording-friendly directory URL. */
@Controller
class GtcConsoleController {
    @GetMapping("/gtc", "/gtc/")
    fun index(): String = "forward:/gtc/index.html"
}
