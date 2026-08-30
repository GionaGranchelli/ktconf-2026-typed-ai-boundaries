package dev.giona.ktconf.notifications

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.stereotype.Service

data class SentEmail(
    val to: String,
    val subject: String,
    val body: String,
)

/** In-memory mail sink used by the demo; it never contacts an SMTP server. */
@Service
class FakeEmailService {
    private val sent = CopyOnWriteArrayList<SentEmail>()

    fun send(to: String, subject: String, body: String) {
        sent += SentEmail(to, subject, body)
    }

    fun sendApprovalRequest(to: String, invoiceId: String, approvalId: String): SentEmail =
        SentEmail(
            to = to,
            subject = "Approval required for invoice $invoiceId",
            body = "Invoice $invoiceId is awaiting human approval. Approval reference: $approvalId.",
        ).also { send(it.to, it.subject, it.body) }

    fun messages(): List<SentEmail> = sent.toList()

    fun count(): Int = sent.size
}
