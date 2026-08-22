# Upstream defect: structured-output schema for Kotlin enums contradicts the parser

Status: **reported upstream** — this conference repository does not patch
around it (see the core rule in README and TRAMAI-INTEGRATION.md).

## Observed behavior

For a structured-output return type containing Kotlin `enum` fields, TramAI
generates a JSON schema that describes each enum as an **object**:

```json
{
  "type": "object",
  "properties": {
    "name":    { "type": "string" },
    "ordinal": { "type": "integer" }
  },
  "required": ["name", "ordinal"],
  "additionalProperties": false
}
```

Real models (verified: `gemma-4-12b-it:q5_k_m`, `gemma4:e4b` via Ollama, and
DeepSeek `deepseek-chat`) follow the schema and emit
`"risk": {"name":"LOW","ordinal":0}` — and the engine then **rejects** the
response with `StructuredOutputException` (3 attempts, parse failure).

## Expected behavior

The schema for a Kotlin enum should describe a flat string, matching what
Jackson's deserializer accepts:

```json
{ "type": "string", "enum": ["LOW", "HIGH"] }
```

With that schema, a compliant model emits `"risk": "LOW"` and the engine
parses it (the parse path already accepts flat strings — the deterministic
providers in this repository prove it).

## Reproduction

1. `ktconf-2026-typed-ai-boundaries`, pinned TramAI `9debb0f2f17…`
2. `export KTCONF_DEMO_LOCAL_BASE_URL=<any OpenAI-compatible endpoint>`
   `export KTCONF_DEMO_LOCAL_MODEL=<model>`
3. `./scripts/demo typed --real`
4. Observe: every real model produces enum objects per the schema; the
   engine rejects them after 3 attempts.

Capture the exact request: point `KTCONF_DEMO_LOCAL_BASE_URL` at a logging
HTTP sink — the prompt contains the full schema (enum fields as objects).

## Relevant TramAI source

`tramai-structured/src/main/kotlin/dev/tramai/structured/JacksonStructuredOutputHandler.kt`

- `schemaForType` (line 133): the `is KClass<*>` branch (line 142) routes
  **every** non-scalar/non-collection Kotlin type — including enums — into
  `objectSchema` (line 175) → `kotlinObjectSchema` (line 189).
- `kotlinObjectSchema` iterates `type.memberProperties`; for a Kotlin enum
  that exposes `name: String` and `ordinal: Int`, producing the object
  schema above.
- Deserialization (`analyze`, line 46) uses Jackson with
  `constructType(targetType.javaType)` — Jackson deserializes enums from
  strings by name.

## Relevant TramAI tests

None cover an enum-typed structured-output field against the generated
schema (the mismatch is schema-generation vs deserialization; existing
handler tests assert parse outcomes, not schema/parser agreement for enums).

## Why the conference demo requires it

`typed --real` demonstrates that a real LLM sits behind the typed boundary.
The demo's `InvoiceAssessment` return type contains two enums
(`InvoiceRisk`, `InvoiceAction`). Until the schema matches the parser, **no**
model can satisfy both: any model following the schema is rejected, and any
model emitting the flat string is technically violating the schema it was
given. The deterministic demo is unaffected (providers are scripted).

## Smallest legitimate upstream change

In `schemaForType`, handle enums before the generic object branch:

```kotlin
is KClass<*> ->
    if ((classifier as KClass<*>).java.isEnum) {
        linkedMapOf(
            "type" to "string",
            "enum" to (classifier as KClass<*>).java.enumConstants
                .map { (it as Enum<*>).name },
        )
    } else {
        objectSchema(classifier, targetType)
    }
```

Followed by a contract test proving schema/parser agreement for an
enum-typed structured output (schema contains `"type":"string"` + enum
names; deserialization accepts those names).
