# Synthetic PDF metadata contract

The contest PDF endpoint accepts `multipart/form-data` with one part named
`file`. The part must be an `application/pdf` document no larger than 5 MiB.

Trusted metadata is embedded in the PDF document information dictionary using
these exact custom properties:

| Property | Values | Rule |
|---|---|---|
| `KTCONF-Classification` | `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED` | Required |
| `KTCONF-Residency` | `ANY`, `EU_ONLY`, `LOCAL_ONLY` | Required |

`PUBLIC` and `INTERNAL` require `ANY`; `CONFIDENTIAL` requires `EU_ONLY`; and
`RESTRICTED` requires `LOCAL_ONLY`. Missing, unknown, malformed, or
contradictory values fail closed with HTTP 400. The endpoint is
`POST /invoices/analyze-pdf`.

The parser reads and validates these properties locally before extracting the
invoice text. Text is limited to `key=value` fields for the synthetic fixtures:
`invoiceId`, `supplierName`, `amountCents`, `currency`, and `description`.
These documents contain synthetic contest data only; the metadata is an input
trust contract for this demo, not a digital signature or legal compliance
claim.
