package dev.giona.ktconf.pdf

/**
 * A PDF or its trusted metadata violates the contest input contract.
 *
 * This is intentionally distinct from unrelated application argument errors so
 * the HTTP layer cannot misreport an internal bug as invalid PDF input.
 */
class InvalidTrustedPdfException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
