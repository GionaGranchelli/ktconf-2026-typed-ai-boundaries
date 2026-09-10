<script setup>
import { computed, ref } from 'vue'
import { analyzeDlpDocument } from '../api.js'
import { providerIdentity, routeToBoundary } from '../model.js'

const file = ref(null)
const busy = ref(false)
const error = ref('')
const result = ref(null)

const boundary = computed(() => {
  const route = result.value?.selectedRoute
  const boundaryId = routeToBoundary(route)
  return boundaryId ? providerIdentity[boundaryId] : null
})

function selectFile(event) {
  const next = event.target.files?.[0]
  if (!next) return
  if (next.type !== 'application/pdf' && !next.name.toLowerCase().endsWith('.pdf')) {
    error.value = 'Please choose the synthetic PDF fixture.'
    return
  }
  file.value = next
  error.value = ''
}

async function analyze() {
  if (!file.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    result.value = await analyzeDlpDocument(file.value)
  } catch (e) {
    error.value = e.message || 'DLP analysis failed.'
  } finally {
    busy.value = false
  }
}

const verdicts = computed(() => {
  if (!result.value) return []
  return [
    { label: 'VALID STRUCTURE', ok: true },
    { label: 'DLP APPLIED', ok: result.value.dlp?.redacted === true },
    { label: 'SAFE TO CROSS', ok: result.value.analysis?.contactEmail === '[EMAIL_REDACTED]' && result.value.analysis?.paymentIban === '[IBAN_REDACTED]' },
  ]
})
</script>

<template>
  <div class="dlp-page">
    <section class="dlp-hero panel">
      <div>
        <span class="eyebrow-label">DLP / Safe Output</span>
        <h2 class="page-title">The output is valid. But is it safe?</h2>
        <p class="page-lede">
          Upload the synthetic confidential PDF and let TramAI redact model-output secrets
          before the typed result crosses into the application and frontend.
        </p>
      </div>
      <div class="dlp-hero__actions">
        <label class="upload-pill">
          <input type="file" accept="application/pdf" @change="selectFile" />
          <span>{{ file ? file.name : 'Choose synthetic PDF' }}</span>
        </label>
        <button class="btn btn--primary" :disabled="!file || busy" @click="analyze">
          {{ busy ? 'Analyzing…' : 'Analyze document' }}
        </button>
      </div>
      <p class="dlp-note">
        Supported claim: <strong>TramAI DLP sanitizes model output before downstream application consumption.</strong>
      </p>
      <p v-if="error" class="error-message">{{ error }}</p>
    </section>

    <section class="dlp-flow panel">
      <div class="dlp-flow__step">Document</div>
      <span>↓</span>
      <div class="dlp-flow__step">AI operation</div>
      <span>↓</span>
      <div class="dlp-flow__step">Valid structured model output</div>
      <span>↓</span>
      <div class="dlp-flow__step dlp-flow__step--accent">TramAI DLP</div>
      <span>↓</span>
      <div class="dlp-flow__step">Sanitized structured result</div>
      <span>↓</span>
      <div class="dlp-flow__step">Application / frontend</div>
    </section>

    <template v-if="result">
      <section class="dlp-grid">
        <article class="panel dlp-card">
          <span class="eyebrow">1. DOCUMENT</span>
          <div class="dlp-card__title">Trusted synthetic input</div>
          <dl class="dlp-meta">
            <div><dt>Document ID</dt><dd>{{ result.document.documentId }}</dd></div>
            <div><dt>Company</dt><dd>{{ result.document.company }}</dd></div>
            <div><dt>Amount</dt><dd>{{ result.document.amount }}</dd></div>
            <div><dt>Purpose</dt><dd>{{ result.document.purpose }}</dd></div>
            <div><dt>Sensitive fields</dt><dd>{{ result.document.sensitiveSignals.join(' · ') }}</dd></div>
          </dl>
        </article>

        <article class="panel dlp-card">
          <span class="eyebrow">2. MODEL</span>
          <div class="dlp-card__title">Governed AI execution</div>
          <dl class="dlp-meta">
            <div><dt>Classification</dt><dd>{{ result.metadata.classification }}</dd></div>
            <div><dt>Residency</dt><dd>{{ result.metadata.residency }}</dd></div>
            <div><dt>Operation</dt><dd>{{ result.operation }}</dd></div>
            <div><dt>Provider</dt><dd>{{ boundary?.provider ?? '—' }}</dd></div>
            <div><dt>Boundary</dt><dd>{{ result.selectedRoute }}</dd></div>
          </dl>
          <div class="model-proof">
            <strong>Sensitive data present in model output contract</strong>
            <span>EMAIL DETECTED</span>
            <span>IBAN DETECTED</span>
          </div>
        </article>

        <article class="panel dlp-card dlp-card--accent">
          <span class="eyebrow">3. DLP</span>
          <div class="dlp-card__title">Sanitized before crossing</div>
          <div class="dlp-status">
            <strong>DLP STATUS</strong>
            <span>{{ result.dlp.replacementCount }} sensitive values redacted</span>
          </div>
          <div class="audit-list">
            <div v-for="entry in result.dlp.audit" :key="entry.ruleId" class="audit-row">
              <strong>{{ entry.ruleId }}</strong>
              <span>{{ entry.decision }}</span>
              <small>replacementCount={{ entry.replacementCount }}</small>
            </div>
          </div>
        </article>

        <article class="panel dlp-card">
          <span class="eyebrow">4. SAFE APPLICATION RESULT</span>
          <div class="dlp-card__title">Typed result after TramAI DLP</div>
          <div class="result-block">
            <strong>Summary</strong>
            <p>{{ result.analysis.summary }}</p>
            <strong>Contact</strong>
            <p>{{ result.analysis.contactEmail }}</p>
            <strong>IBAN</strong>
            <p>{{ result.analysis.paymentIban }}</p>
          </div>
        </article>
      </section>

      <section class="dlp-verdict panel">
        <div v-for="item in verdicts" :key="item.label" class="verdict-pill" :class="{ 'verdict-pill--ok': item.ok }">
          <span>{{ item.ok ? '✓' : '•' }}</span>
          <strong>{{ item.label }}</strong>
        </div>
      </section>

      <section class="panel dlp-proof">
        <span class="eyebrow-label">WHY THIS MATTERS</span>
        <h3>Types tell us whether the output is valid. DLP tells us whether it is safe to cross.</h3>
        <p>
          The provider can return a structurally valid response containing the synthetic email and IBAN.
          TramAI removes them at the model-output boundary, and the frontend receives only the redacted typed result.
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.dlp-page{max-width:1280px;margin:0 auto;display:grid;gap:18px}.dlp-hero{padding:28px 30px;display:grid;gap:16px;border-color:var(--line-strong);background:radial-gradient(circle at 100% 0,rgba(118,185,0,.1),transparent 32%),var(--panel)}.dlp-hero__actions{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.upload-pill{display:inline-flex;align-items:center;min-height:44px;padding:0 16px;border:1px dashed var(--line-mid);border-radius:999px;background:rgba(255,255,255,.02);cursor:pointer;position:relative;overflow:hidden}.upload-pill input{position:absolute;inset:0;opacity:0;cursor:pointer}.upload-pill span{font:600 12px var(--font-mono);color:var(--text-bright)}.dlp-note{margin:0;color:var(--muted);font-size:12px;line-height:1.6}.error-message{margin:0;color:#ff9e9e;font:600 12px var(--font-mono)}.dlp-flow{display:flex;flex-wrap:wrap;align-items:center;gap:10px;padding:18px 20px}.dlp-flow span{color:var(--accent);font-size:18px}.dlp-flow__step{padding:10px 12px;border:1px solid var(--line);border-radius:999px;background:rgba(255,255,255,.02);font:700 11px var(--font-mono);color:var(--text-bright)}.dlp-flow__step--accent{border-color:var(--accent);background:var(--accent-dim);color:var(--accent-bright)}.dlp-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.dlp-card{padding:24px;display:grid;gap:18px;min-height:290px}.dlp-card--accent{border-color:var(--accent)}.dlp-card__title{color:var(--text-bright);font-size:22px;line-height:1.05;letter-spacing:-.04em}.dlp-meta{display:grid;gap:10px;margin:0}.dlp-meta div{display:flex;justify-content:space-between;gap:16px;padding-bottom:10px;border-bottom:1px solid var(--line)}.dlp-meta dt{color:var(--muted);font:700 10px var(--font-mono);letter-spacing:.12em;text-transform:uppercase}.dlp-meta dd{margin:0;color:var(--text-bright);text-align:right;font-size:12px}.model-proof{margin-top:auto;display:grid;gap:8px;padding:14px;border:1px solid var(--line);border-radius:14px;background:rgba(255,255,255,.018)}.model-proof strong{color:var(--text-bright);font-size:13px}.model-proof span,.audit-row span{color:var(--accent-bright);font:700 11px var(--font-mono)}.dlp-status{display:grid;gap:8px;padding:16px;border:1px solid rgba(118,185,0,.28);border-radius:16px;background:rgba(118,185,0,.08)}.dlp-status strong{color:var(--accent-bright);font:800 10px var(--font-mono);letter-spacing:.14em}.dlp-status span{color:var(--text-bright);font-size:20px;line-height:1.15}.audit-list{display:grid;gap:10px}.audit-row{display:grid;gap:4px;padding:12px 14px;border:1px solid var(--line);border-radius:14px;background:rgba(255,255,255,.018)}.audit-row strong{color:var(--text-bright);font-size:14px;text-transform:uppercase}.audit-row small{color:var(--muted);font:700 10px var(--font-mono)}.result-block{display:grid;gap:8px}.result-block strong{color:var(--accent-bright);font:800 10px var(--font-mono);letter-spacing:.15em;text-transform:uppercase}.result-block p{margin:0 0 8px;color:var(--text-bright);font-size:17px;line-height:1.4}.dlp-verdict{display:flex;gap:10px;flex-wrap:wrap;padding:18px 20px}.verdict-pill{display:inline-flex;align-items:center;gap:8px;padding:10px 14px;border:1px solid var(--line);border-radius:999px;color:var(--muted)}.verdict-pill--ok{border-color:var(--accent);color:var(--accent-bright)}.dlp-proof{padding:24px 26px;display:grid;gap:12px}.dlp-proof h3{margin:0;color:var(--text-bright);font-size:28px;line-height:1.02;letter-spacing:-.05em}.dlp-proof p{margin:0;color:var(--muted-light);font-size:13px;line-height:1.7}
@media (max-width:920px){.dlp-grid{grid-template-columns:1fr}.dlp-flow{display:grid;grid-template-columns:1fr;justify-items:start}.dlp-flow span{transform:rotate(90deg)}}
</style>
