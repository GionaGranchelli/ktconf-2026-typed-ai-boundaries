package dev.giona.ktconf.api

import dev.giona.ktconf.application.DlpDemoResult
import dev.giona.ktconf.application.DlpDemoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/documents/dlp")
class DlpDemoController(
    private val service: DlpDemoService,
) {
    @PostMapping("/analyze", consumes = ["multipart/form-data"])
    suspend fun analyze(
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<DlpDemoResult> = ResponseEntity.ok(service.analyze(file))
}
