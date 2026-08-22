package dev.giona.ktconf.governance

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Fixed demo clock — deterministic timestamps across every instance, the
 * same choice v2 made. Approval ids are unique per suspension (the API
 * returns them to the client), so they are NOT fixed like v2's CLI ids.
 */
val demoClock: Clock = Clock.fixed(
    Instant.parse("2026-09-18T10:15:00Z"),
    ZoneId.of("UTC"),
)

const val INVOICE_MODEL = "invoice-model"
const val LOCAL_PROVIDER = "local-provider"
const val CLOUD_PROVIDER = "conference-cloud-provider"
const val REAL_PROVIDER = "real-provider"
