# DLP story — KTConf presentation additions

The binary slide deck is not edited in this repository. Use this document as
the source of truth for the three DLP slides to add to the talk.

## Slide 1 — The problem

**Title:** The output is valid. But is it safe?

Show a typed `DocumentAnalysis` that is structurally correct but still carries
sensitive values:

```text
contactEmail = finance@...
paymentIban  = NL91...
```

**Message**

```text
Schema validation asks:
"Is this valid?"

DLP asks:
"Is this safe to cross?"
```

## Slide 2 — The boundary

**Title:** DLP is another deterministic boundary

**Visual**

```text
PDF
 ↓
Model
 ↓
VALID structured output
 ↓
TRAMAI DLP
 ↓
[EMAIL_REDACTED]
[IBAN_REDACTED]
 ↓
Application
```

**Key line**

> The model may see information required for its task. That does not mean every downstream consumer should receive it.

Be explicit that this demo covers **model-output DLP** only.

## Slide 3 — The proof

**Title:** We can prove redaction without storing the secret

Show:

```text
Frontend
✓ [EMAIL_REDACTED]
✓ [IBAN_REDACTED]

Logs
rule=email replacements=1
rule=iban replacements=1

Observability
classification=CONFIDENTIAL
operation=analyzeDocument
dlp=applied

Audit
REDACTED
ruleId=email
replacementCount=1
```

**Closing line**

> Record the decision, not the secret.
