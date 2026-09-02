<!--
  DocumentFlowPage (Live Governance) — Hero demo screen.

  Flow: PDF upload → trusted metadata → TramAI route decision →
        Typed assessment → HIGH-risk suspension → human approve/deny →
        payment counter proof → audit evidence → replay protection proof

  P0-2: DenialProof panel — intentional forbidden route with delta=0 proof
  P0-4: Context preserved through 202 suspension (approval enriched)
  P1-1: proveReplayRejection() — duplicate approve → must be rejected
  P1-2: AuditTimeline — renders evidence.auditEvents hash chain
  P1-4: ProofStrip — persistent screenshot-moment summary bar

  INVARIANT: The UI never classifies, routes, or authorizes anything.
  All proof values come from backend responses only.
-->
<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import BoundaryCard   from '../components/BoundaryCard.vue'
import WorkflowTrace  from '../components/WorkflowTrace.vue'
import AuditTimeline  from '../components/AuditTimeline.vue'
import ProofStrip     from '../components/ProofStrip.vue'
import {
  analyzePdf, approve, deny, getEvidence, getStats, attemptForbiddenRoute,
} from '../api.js'
import {
  allowedBoundaries, boundaries, counterDelta,
  formatMoney, riskPillClass, routeToBoundary,
} from '../model.js'

const emit = defineEmits(['stats-updated'])
const props = defineProps({ demoFocus: { type: String, default: null } })

onMounted(() => {
  if (props.demoFocus) nextTick(() => document.querySelector(`[data-demo-panel="${props.demoFocus}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
})

// ── Core workflow state ────────────────────────────────────────
const file          = ref(null)
const dragging      = ref(false)
const busy          = ref(false)
const loadingMessage = ref('')
const localError    = ref('')
const metadata      = ref(null)
const invoice       = ref(null)
const assessment    = ref(null)
const selectedRoute = ref(null)
const approval      = ref(null)
const evidence      = ref(null)
const approvalDecision = ref(null)
const statsBefore   = ref(null)
const statsAfter    = ref(null)
const lastAction    = ref('Waiting for a classified document')

// ── P1-1: replay protection state ─────────────────────────────
const replayResult  = ref(null)

// ── P0-2: denial proof state ───────────────────────────────────
const denialBusy    = ref(false)
const denialResult  = ref(null)
const forceGlobalRoute = ref(false)
const useSelectedForDenial = computed(() =>
  Boolean(file.value && metadata.value && metadata.value.residency !== 'ANY')
)

// ── Derived ────────────────────────────────────────────────────
const allowedSet       = computed(() => allowedBoundaries(metadata.value))
const selectedBoundary = computed(() => routeToBoundary(selectedRoute.value))
const amount           = computed(() => assessment.value
  ? formatMoney(assessment.value.amountCents, assessment.value.currency)
  : '—')
const hasRun = computed(() =>
  Boolean(metadata.value || assessment.value || approval.value))
const pendingHighRiskReview = computed(() =>
  Boolean(assessment.value?.risk === 'HIGH' && !approval.value && !evidence.value))
const pendingAutomaticPayment = computed(() =>
  Boolean(assessment.value?.risk === 'LOW' && assessment.value?.recommendedAction !== 'SCHEDULE_PAYMENT' && !approval.value && !evidence.value))
const flowComplete = computed(() =>
  Boolean(assessment.value && !busy.value && !pendingHighRiskReview.value && !pendingAutomaticPayment.value && (!approval.value || evidence.value)))
const paymentStatus = computed(() => {
  if (!assessment.value) return { label: 'NOT EVALUATED', detail: 'No invoice assessment yet.', tone: 'neutral' }
  if (!approval.value && assessment.value.recommendedAction === 'SCHEDULE_PAYMENT') return { label: 'PAID', detail: `Low-risk auto-payment executed · ledger count: ${statsAfter.value?.paymentExecutionCount ?? '—'}`, tone: 'success' }
  if (!approval.value && assessment.value.risk === 'HIGH') return { label: 'REVIEW REQUIRED', detail: 'High-risk assessment. No payment tool was requested on this route.', tone: 'pending' }
  if (!approval.value) return { label: 'AUTO PAYMENT PENDING', detail: 'LOW risk was established, but the auto-schedule-payment tool did not execute.', tone: 'pending' }
  if (!evidence.value) return { label: 'AWAITING APPROVAL', detail: 'Payment execution count: 0 before human approval.', tone: 'pending' }
  if (approvalDecision.value === 'approve') {
    return { label: 'SCHEDULED', detail: `Ledger execution count: ${statsAfter.value?.paymentExecutionCount ?? '—'} · exactly once`, tone: 'success' }
  }
  return { label: 'DENIED', detail: `Payment execution count: ${statsAfter.value?.paymentExecutionCount ?? '0'} · no side effect`, tone: 'denied' }
})

const providerDeltas = computed(() => {
  const result = {}
  for (const b of boundaries) {
    result[b.id] = counterDelta(statsBefore.value, statsAfter.value, b.statsKey)
  }
  return result
})

// P0-2: delta for the denied (global) route
const deltaGlobal = computed(() =>
  denialResult.value
    ? (denialResult.value.after?.globalNvidiaInvocationCount ?? 0)
      - (denialResult.value.before?.globalNvidiaInvocationCount ?? 0)
    : null
)
const providerDeltaZero = computed(() => deltaGlobal.value === 0)

// Workflow step index (1–5) for step indicator
const step = computed(() => {
  if (flowComplete.value)  return 6
  if (evidence.value)   return 5
  if (approval.value)   return 4
  if (assessment.value) return 3
  if (metadata.value)   return 2
  if (busy.value)       return 2
  return 1
})

// ── File handling ──────────────────────────────────────────────
function selectFiles(files) {
  const f = files?.[0]
  if (!f) return
  if (f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) {
    localError.value = 'Please choose a PDF document.'
    return
  }
  file.value = f
  localError.value = ''
}

function onDrop(event) {
  dragging.value = false
  selectFiles(event.dataTransfer.files)
}

// ── Process document ───────────────────────────────────────────
async function processDocument() {
  if (!file.value || busy.value) return
  busy.value      = true
  loadingMessage.value = 'Reading trusted metadata and evaluating TramAI policy…'
  localError.value = ''
    metadata.value  = null
    invoice.value   = null
  assessment.value= null
  selectedRoute.value = null
  approval.value  = null
  evidence.value  = null
  replayResult.value = null
  lastAction.value= 'Reading trusted metadata · evaluating TramAI policy…'

  try {
    statsBefore.value = await getStats()
    const result = await analyzePdf(file.value, forceGlobalRoute.value ? 'GLOBAL_CLOUD' : null)

    metadata.value      = result.metadata      ?? null
    invoice.value       = result.invoice       ?? null
    selectedRoute.value = result.selectedRoute  ?? null
    assessment.value    = result.assessment     ?? null

    if (result.approvalId) {
      approval.value = result
      lastAction.value = 'Model finished reasoning · runtime awaiting human authority'
    } else {
      lastAction.value = selectedRoute.value
        ? `TramAI selected ${selectedRoute.value}`
        : 'Analysis complete'
    }

    statsAfter.value = await getStats()
    emit('stats-updated', statsAfter.value)
  } catch (e) {
    localError.value = e.message || 'Document processing failed.'
    lastAction.value = 'Policy denied or request rejected'
    try {
      statsAfter.value = await getStats()
      emit('stats-updated', statsAfter.value)
    } catch { /* preserve original error */ }
  } finally {
    busy.value = false
    loadingMessage.value = ''
  }
}

// ── Approve ────────────────────────────────────────────────────
async function approvePayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  loadingMessage.value = 'Resuming the governed workflow after approval…'
  localError.value = ''
  try {
    assessment.value = await approve(approval.value.approvalId)
    approvalDecision.value = 'approve'
    statsAfter.value = await getStats()
    evidence.value   = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human approved · payment executed · workflow resumed'
    emit('stats-updated', statsAfter.value)
  } catch (e) {
    localError.value = e.message || 'Approval failed.'
  } finally {
    busy.value = false
    loadingMessage.value = ''
  }
}

// ── Deny ───────────────────────────────────────────────────────
async function denyPayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  loadingMessage.value = 'Recording the denial and closing the governed workflow…'
  localError.value = ''
  try {
    await deny(approval.value.approvalId)
    approvalDecision.value = 'deny'
    statsAfter.value = await getStats()
    evidence.value   = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human denied · side effect remains blocked'
    emit('stats-updated', statsAfter.value)
  } catch (e) {
    localError.value = e.message || 'Denial failed.'
  } finally {
    busy.value = false
    loadingMessage.value = ''
  }
}

// ── P1-1: Replay protection proof ─────────────────────────────
async function proveReplayRejection() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  loadingMessage.value = 'Checking replay protection against TramAI…'
  localError.value = ''
  try {
    const statsBeforeReplay = await getStats().catch(() => statsAfter.value)
    try {
      await approve(approval.value.approvalId)
      replayResult.value = {
        rejected: false,
        message: 'UNEXPECTED: duplicate approval was executed by backend.',
      }
    } catch (e) {
      const statsAfterReplay = await getStats().catch(() => statsAfter.value)
      const paymentDelta = (statsAfterReplay?.paymentExecutionCount ?? 0) - (statsBeforeReplay?.paymentExecutionCount ?? 0)
      const isExpectedError = e.status === 409
      const exactOnceVerified = isExpectedError && paymentDelta === 0

      replayResult.value = {
        rejected: exactOnceVerified,
        status: e.status,
        message: exactOnceVerified
          ? `Duplicate approval rejected by TramAI (HTTP ${e.status}). Payment count unchanged (${statsAfterReplay?.paymentExecutionCount ?? 1}).`
          : `Replay test failed: ${e.message || `HTTP ${e.status}`}`,
      }
    }
  } finally {
    try { statsAfter.value = await getStats(); emit('stats-updated', statsAfter.value) } catch {}
    busy.value = false
    loadingMessage.value = ''
  }
}

// ── P0-2: Denial proof ────────────────────────────────────────
async function runForbiddenRouteProof() {
  if (denialBusy.value || !useSelectedForDenial.value) return
  denialBusy.value = true
  denialResult.value = null
  try {
    const before = await getStats()
    try {
      // The hero proof always reuses the selected restricted/EU PDF. This keeps
      // the allowed and denied runs semantically tied to the same document.
      await attemptForbiddenRoute(file.value)
      // Should never succeed for the selected restricted/EU document.
      const after = await getStats()
      denialResult.value = { before, after, unexpectedSuccess: true, isDenied: false }
    } catch (e) {
      const after = await getStats()
      const isDenied = (e.status === 403)
      const reasonCode = (typeof e.body === 'object' && (e.body?.reasonCode || e.body?.message))
        ? (e.body.reasonCode || e.body.message)
        : (e.message || 'classification-routing-blocked')
      denialResult.value = {
        status: e.status,
        reasonCode,
        isDenied,
        before,
        after,
        unexpectedSuccess: false,
      }
    }
  } finally {
    denialBusy.value = false
  }
}

// ── Reset ─────────────────────────────────────────────────────
function reset() {
  file.value = null
  metadata.value = null
  invoice.value = null
  assessment.value = null
  selectedRoute.value = null
  approval.value = null
  evidence.value = null
  approvalDecision.value = null
  statsBefore.value = null
  statsAfter.value = null
  localError.value = ''
  lastAction.value = 'Waiting for a classified document'
  replayResult.value = null
  denialResult.value = null
}
</script>

<template>
  <div>
    <div v-if="busy" class="loading-overlay" role="status" aria-live="polite">
      <div class="loading-card">
        <div class="loading-orbit"><span /><span /><span /></div>
        <div class="loading-title">TramAI is governing this step</div>
        <div class="loading-message">{{ loadingMessage || 'Working…' }}</div>
        <div class="loading-track"><span /></div>
        <div class="loading-caption">The browser is waiting for the backend response. No side effect executes before authorization.</div>
      </div>
    </div>
    <!-- Step indicator -->
    <div v-if="props.demoFocus" class="demo-intent" :class="`demo-intent--${props.demoFocus}`">
      <strong>{{ props.demoFocus === 'action' ? 'ACTION GOVERNANCE PROOF' : 'DATA PLACEMENT PROOF' }}</strong>
      <span>{{ props.demoFocus === 'action' ? 'Upload the RESTRICTED / LOCAL_ONLY payment PDF to reach the human approval gate.' : 'Upload the CONFIDENTIAL / EU_ONLY PDF, then force GLOBAL_CLOUD to prove the provider delta stays at 0.' }}</span>
    </div>
    <div class="step-indicator">
      <div class="step" :class="{ 'step--done': step > 1, 'step--active': step === 1 }">
        <span class="step__num">1</span><span class="step__label">Input</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 2, 'step--active': step === 2 }">
        <span class="step__num">2</span><span class="step__label">Governance</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 3, 'step--active': step === 3 }">
        <span class="step__num">3</span><span class="step__label">Model result</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 4, 'step--active': step === 4 }">
        <span class="step__num">4</span><span class="step__label">Authority</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step >= 5, 'step--active': step === 5 }">
        <span class="step__num">5</span><span class="step__label">Evidence</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step === 6, 'step--active': step === 6 }">
        <span class="step__num">6</span><span class="step__label">Complete</span>
      </div>
    </div>

    <!-- Live workflow trace -->
    <WorkflowTrace
      class="gap-top"
      :file="file"
      :busy="busy"
      :metadata="metadata"
      :selected-route="selectedRoute"
      :assessment="assessment"
      :approval="approval"
      :evidence="evidence"
      :stats-before="statsBefore"
      :stats-after="statsAfter"
    />

    <!-- P0-2: Policy Denial Proof -->
    <div class="panel gap-top panel--denial-proof">
      <div class="panel-heading">
        <div>
          <span class="eyebrow">Policy denial proof</span>
          <div class="panel-title">{{ useSelectedForDenial ? `Attempt ${metadata.classification} → GLOBAL CLOUD` : 'Attempt RESTRICTED → GLOBAL CLOUD' }}</div>
          <div class="panel-subtitle">{{ useSelectedForDenial ? 'Reusing the selected PDF with an explicit forced route' : 'TramAI must deny before any provider is invoked · delta must be 0' }}</div>
        </div>
        <button v-if="useSelectedForDenial" class="btn btn--ghost btn--sm" :disabled="denialBusy" @click="runForbiddenRouteProof">
          <span v-if="denialBusy" class="spinner" />
          {{ denialBusy ? 'Verifying…' : 'Force route & prove denial' }}
        </button>
      </div>

      <div v-if="denialResult" class="denial-result">
        <div v-if="denialResult.unexpectedSuccess" class="denial-result__fail">
          ⚠ UNEXPECTED: provider was reached — TramAI policy breach!
        </div>
        <template v-else-if="denialResult.isDenied && providerDeltaZero">
          <div class="denial-result__status">
            <span class="pill pill--denied">403 DENIED</span>
            <span class="denial-result__reason font-mono">
              {{ denialResult.reasonCode }}
            </span>
          </div>
          <div class="denial-result__counters">
            <div class="metric-cell">
              <span class="metric-label">Global NVIDIA before</span>
              <span class="metric-value">{{ denialResult.before?.globalNvidiaInvocationCount ?? '—' }}</span>
            </div>
            <div class="metric-cell">
              <span class="metric-label">Global NVIDIA after</span>
              <span class="metric-value">{{ denialResult.after?.globalNvidiaInvocationCount ?? '—' }}</span>
            </div>
            <div class="metric-cell">
              <span class="metric-label">Delta</span>
              <span class="metric-value text-accent">0 ✓</span>
            </div>
            <div class="metric-cell">
              <span class="metric-label">Provider status</span>
              <span class="metric-value" style="font-size:0.85rem;">NEVER INVOKED</span>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="denial-result__status">
            <span class="pill pill--high">PROOF FAILED (HTTP {{ denialResult.status || 'ERROR' }})</span>
            <span class="denial-result__reason font-mono">
              {{ denialResult.reasonCode }}
            </span>
          </div>
        </template>
      </div>
      <div v-else class="info-note">
        Select a CONFIDENTIAL/EU_ONLY or RESTRICTED/LOCAL_ONLY PDF to run this proof.
        The same selected document is forced to GLOBAL_CLOUD; TramAI must block it before the provider is called.
      </div>
    </div>

    <!-- Row 1: Input + Governance -->
    <div class="grid-doc gap-top">
      <!-- 01 · INPUT -->
      <div class="panel" data-demo-panel="input">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">01 · Input</span>
            <div class="panel-title">Classified document</div>
            <div class="panel-subtitle">Trusted metadata parsed locally before any model call</div>
          </div>
          <button v-if="hasRun || file" class="text-btn" @click="reset">Reset</button>
        </div>

        <label
          class="dropzone"
          :class="{ 'dropzone--active': dragging, 'dropzone--loaded': file }"
          @dragenter.prevent="dragging = true"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
        >
          <input type="file" accept="application/pdf,.pdf" @change="selectFiles($event.target.files)" />
          <span class="dropzone__icon">PDF</span>
          <strong class="dropzone__title">
            {{ file ? file.name : 'Drop a classified PDF here' }}
          </strong>
          <span class="dropzone__hint">
            {{ file ? `${Math.ceil(file.size / 1024)} KB · ready` : 'or click to choose a file' }}
          </span>
        </label>

        <button
          class="btn btn--primary btn--full"
          style="margin-top: 12px;"
          :disabled="!file || busy"
          @click="processDocument"
        >
          <span v-if="busy" class="spinner" />
          {{ busy ? 'Evaluating under policy…' : 'Process under TramAI policy' }}
        </button>

        <div v-if="metadata" class="meta-grid">
          <div class="meta-cell">
            <span class="meta-label">Classification</span>
            <span class="meta-value">{{ metadata.classification }}</span>
          </div>
          <div class="meta-cell">
            <span class="meta-label">Residency</span>
            <span class="meta-value">{{ metadata.residency }}</span>
          </div>
        </div>

        <div v-if="file" class="route-proof-switch">
          <label class="route-proof-switch__toggle"><input v-model="forceGlobalRoute" type="checkbox" /><span /></label>
          <span class="route-proof-switch__copy"><strong>Force GLOBAL_CLOUD for this upload</strong><small>Use the same PDF with an explicit cloud route. TramAI allows PUBLIC data and denies RESTRICTED/EU_ONLY data.</small></span>
          <button v-if="useSelectedForDenial" class="btn btn--ghost btn--sm" :disabled="denialBusy || !forceGlobalRoute" @click="runForbiddenRouteProof">
            {{ denialBusy ? 'Checking…' : 'Run denial proof' }}
          </button>
        </div>

        <p class="microcopy">
          Trusted metadata is read server-side before inference.
          This UI displays backend evidence; it does not classify documents.
        </p>
      </div>

      <!-- 02 · GOVERNANCE DECISION -->
      <div class="panel" data-demo-panel="authority">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">02 · Authority</span>
            <div class="panel-title">TramAI governance decision</div>
            <div class="panel-subtitle">One policy plane · three execution boundaries</div>
          </div>
          <span class="action-log">{{ lastAction }}</span>
        </div>

        <div class="grid-3">
          <BoundaryCard
            v-for="b in boundaries"
            :key="b.id"
            :boundary="b"
            :allowed="allowedSet.has(b.id)"
            :selected="selectedBoundary === b.id"
            :delta="providerDeltas[b.id] ?? null"
          />
        </div>

        <div v-if="selectedRoute && !selectedBoundary" class="legacy-note">
          Backend route: <strong>{{ selectedRoute }}</strong>.
          This is a legacy deterministic route — not a governed NVIDIA execution boundary.
        </div>
      </div>
    </div>

    <!-- Row 2: Model result + Authority + Evidence -->
    <div class="grid-3 gap-top">
      <!-- 03 · MODEL RESULT -->
      <div class="panel">
        <span class="eyebrow">03 · Model result</span>
        <div v-if="assessment">
          <div class="assessment-amount">{{ amount }}</div>
          <div class="assessment-supplier">{{ assessment.supplierName }}</div>
          <div class="assessment-tags">
            <span class="pill" :class="riskPillClass(assessment.risk)">{{ assessment.risk }}</span>
            <span class="pill pill--neutral">{{ assessment.recommendedAction }}</span>
            <span class="pill pill--neutral">{{ Math.round((assessment.confidence ?? 0) * 100) }}% confidence</span>
          </div>
          <p class="assessment-rationale">{{ assessment.rationale }}</p>
        </div>
        <div v-else-if="invoice" class="invoice-context">
          <span class="context-label">INVOICE CONTEXT · BEFORE APPROVAL</span>
          <div class="assessment-amount">{{ formatMoney(invoice.amountCents, invoice.currency) }}</div>
          <div class="assessment-supplier">{{ invoice.supplierName }}</div>
          <div class="assessment-tags">
            <span class="pill pill--high">HIGH-RISK WRITE</span>
            <span class="pill pill--neutral">{{ invoice.invoiceId }}</span>
          </div>
          <p class="assessment-rationale">{{ invoice.description }}</p>
          <p class="microcopy">The model requested <code>schedule-payment</code>; TramAI suspended it before execution. Review the trusted invoice details before deciding.</p>
        </div>
        <div v-else class="empty-state">
          The selected provider’s typed assessment appears here after an allowed inference.
        </div>
      </div>

      <!-- 04 · CONSEQUENTIAL ACTION -->
      <div class="panel" :class="{ 'panel--accent': approval }">
        <span class="eyebrow">04 · Consequential action</span>
        <div class="payment-status" :class="`payment-status--${paymentStatus.tone}`">
          <div>
            <span class="metric-label">PAYMENT STATUS</span>
            <strong>{{ paymentStatus.label }}</strong>
          </div>
          <span class="payment-status__detail">{{ paymentStatus.detail }}</span>
        </div>
        <template v-if="approval">
          <div class="panel-title" style="margin-top:10px; font-size:1.05rem;">{{ evidence ? 'Governed payment result' : 'Human authority required' }}</div>
          <p class="authority-copy" style="margin-top:8px;">
            The model proposed
            <span class="authority-tool">{{ approval.toolName }}</span>
            TramAI suspended execution before the side effect.
            Payment count right now:
            <strong>{{ statsAfter?.paymentExecutionCount ?? '—' }}</strong>
          </p>
          <div v-if="approval.notificationStatus" class="notification-receipt">
            <span class="notification-receipt__icon">✉</span>
            <span><strong>Approval email {{ approval.notificationStatus.toLowerCase() }}</strong><small>{{ approval.notificationSubject }} · {{ approval.notificationRecipient }}</small></span>
          </div>
          <div class="authority-actions">
            <button class="btn btn--primary" :disabled="busy || !!evidence" @click="approvePayment">
              <span v-if="busy" class="spinner" />
              Approve
            </button>
            <button class="btn btn--danger" :disabled="busy || !!evidence" @click="denyPayment">Deny</button>
          </div>
          <div class="approval-id">Approval ID: {{ approval.approvalId }}</div>

          <!-- P1-1: Replay protection proof -->
          <template v-if="replayResult">
            <div class="replay-result" :class="replayResult.rejected ? 'replay-result--rejected' : 'replay-result--warn'">
              <span class="replay-result__label">Replay {{ replayResult.rejected ? 'REJECTED' : 'UNEXPECTED' }}</span>
              <span class="replay-result__msg">{{ replayResult.message }}</span>
              <span v-if="replayResult.rejected" class="pill pill--allowed" style="margin-top:4px;font-size:9px;">exactly-once enforced ✓</span>
            </div>
          </template>
          <button
            v-else-if="evidence && !replayResult"
            class="btn btn--ghost btn--sm btn--full"
            style="margin-top:10px;"
            :disabled="busy"
            @click="proveReplayRejection"
          >
            Prove replay protection
          </button>
        </template>
        <div v-else class="empty-state">
          A HIGH-risk tool call suspends here before executing any side effect.
        </div>
      </div>

      <!-- 05 · EVIDENCE -->
      <div class="panel">
        <span class="eyebrow">05 · Evidence</span>

        <div class="metric-grid">
          <div class="metric-cell" v-for="b in boundaries" :key="b.id">
            <span class="metric-label">{{ b.title }}</span>
            <span class="metric-value">{{ statsAfter?.[b.statsKey] ?? '—' }}</span>
          </div>
          <div class="metric-cell">
            <span class="metric-label">Payments</span>
            <span class="metric-value">{{ statsAfter?.paymentExecutionCount ?? '—' }}</span>
          </div>
        </div>

        <!-- P1-2: Real audit event timeline -->
        <div style="margin-top: 14px;">
          <AuditTimeline
            :events="evidence?.auditEvents ?? []"
            :chain-valid="evidence?.chainValid ?? null"
          />
        </div>

        <p class="microcopy">
          Evidence is fetched from the backend after approval/denial.
          Chain validity comes from hash-chained server records.
        </p>
      </div>
    </div>

    <!-- P1-4: Proof strip -->
    <ProofStrip
      :metadata="metadata"
      :selected-route="selectedRoute"
      :assessment="assessment"
      :approval="approval"
      :evidence="evidence"
      :stats-before="statsBefore"
      :stats-after="statsAfter"
      :replay-result="replayResult"
      :denial-result="denialResult"
    />

    <!-- Error banner -->
    <div v-if="localError" class="alert-banner alert-banner--error" role="alert">
      <div>
        <span class="alert-banner__label">Policy / request result</span>
        <span class="alert-banner__msg">{{ localError }}</span>
      </div>
      <button class="alert-banner__close" @click="localError = ''" aria-label="Dismiss">×</button>
    </div>
  </div>
</template>
