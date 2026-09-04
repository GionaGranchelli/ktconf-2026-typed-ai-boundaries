package dev.giona.ktconf.api

import dev.giona.ktconf.application.DocumentHistoryRecord
import dev.giona.ktconf.application.DocumentHistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/governance/documents")
class DocumentHistoryController(private val history: DocumentHistoryService) {
    @GetMapping
    fun list(): List<DocumentHistoryRecord> = history.list()

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): DocumentHistoryRecord = history.get(id)
}
