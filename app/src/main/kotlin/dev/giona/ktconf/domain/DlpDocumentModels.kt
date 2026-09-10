package dev.giona.ktconf.domain

import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification

data class ConfidentialDocument(
    val documentId: String,
    val company: String,
    val amount: String,
    val purpose: String,
    val paymentIban: String,
    val contactEmail: String,
    val notes: String,
)

data class DocumentAnalysis(
    val documentId: String,
    val company: String,
    val amount: String,
    val summary: String,
    val contactEmail: String,
    val paymentIban: String,
)

fun ConfidentialDocument.toClassifiedDocument(
    source: ClassificationSource,
): ClassifiedDocument<ConfidentialDocument> =
    ClassifiedDocument(
        payload = this,
        classification = DataClassification.CONFIDENTIAL,
        source = source,
    )
