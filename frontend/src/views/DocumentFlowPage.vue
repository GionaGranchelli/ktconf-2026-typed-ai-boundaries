<!--
  DocumentFlowPage — Main demo screen.

  Flow: PDF upload → trusted metadata → TramAI route decision →
        Nemotron assessment → HIGH-risk suspension → human approve/deny →
        payment counter proof → audit evidence

  INVARIANT: The UI never classifies, routes, or authorizes anything.
  All proof values come from backend responses only.
-->
<script setup>
import { computed, ref } from 'vue'
import BoundaryCard from '../components/BoundaryCard.vue'
import {
  analyzePdf, approve, deny, getEvidence, getStats,
} from '../api.js'
import {
  allowedBoundaries, boundaries, counterDelta,
  formatMoney, riskPillClass, routeToBoundary,
} from '../model.js'

const emit = defineEmits(['stats-updated'])

// ── State ──────────────────────────────────────────────────────
const file         = ref(null)
const dragging     = ref(false)
const busy         = ref(false)
const localError   = ref('')
const metadata     = ref(null)
const assessment   = ref(null)
const selectedRoute= ref(null)
const approval     = ref(null)
const evidence     = ref(null)
const statsBefore  = ref(null)
const statsAfter   = ref(null)
const lastAction   = ref('Waiting for a classified document')

// ── Derived ────────────────────────────────────────────────────
const allowedSet       = computed(() => allowedBoundaries(metadata.value))
const selectedBoundary = computed(() => routeToBoundary(selectedRoute.value))
const amount           = computed(() => assessment.value
  ? formatMoney(assessment.value.amountCents, assessment.value.currency)
  : '—')
const hasRun = computed(() =>
  Boolean(metadata.value || assessment.value || approval.value))

const providerDeltas = computed(() => {
  const result = {}
  for (const b of boundaries) {
    result[b.id] = counterDelta(statsBefore.value, statsAfter.value, b.statsKey)
  }
  return result
})

// Workflow step index (1–5)
const step = computed(() => {
  if (evidence.value)  return 5
  if (approval.value)  return 4
  if (assessment.value)return 3
  if (metadata.value)  return 2
  if (busy.value)      return 2
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

// ── Actions ────────────────────────────────────────────────────
async function processDocument() {
  if (!file.value || busy.value) return
  busy.value     = true
  localError.value = ''
  metadata.value  = null
  assessment.value= null
  selectedRoute.value = null
  approval.value  = null
  evidence.value  = null
  lastAction.value= 'Reading trusted metadata · evaluating TramAI policy…'

  try {
    statsBefore.value = await getStats()
    const result = await analyzePdf(file.value)

    metadata.value      = result.metadata      ?? null
    selectedRoute.value = result.selectedRoute  ?? null
    assessment.value    = result.assessment     ?? null

    if (result.approvalId) {
      approval.value  = result
      lastAction.value= 'Model finished reasoning · runtime awaiting human authority'
    } else {
      lastAction.value= selectedRoute.value
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
  }
}

async function approvePayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  localError.value = ''
  try {
    assessment.value = await approve(approval.value.approvalId)
    statsAfter.value = await getStats()
    evidence.value   = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human approved · payment executed · workflow resumed'
    emit('stats-updated', statsAfter.value)
  } catch (e) {
    localError.value = e.message || 'Approval failed.'
  } finally {
    busy.value = false
  }
}

async function denyPayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  localError.value = ''
  try {
    await deny(approval.value.approvalId)
    statsAfter.value = await getStats()
    evidence.value   = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human denied · side effect remains blocked'
    emit('stats-updated', statsAfter.value)
  } catch (e) {
    localError.value = e.message || 'Denial failed.'
  } finally {
    busy.value = false
  }
}

function reset() {
  file.value = null
  metadata.value = null
  assessment.value = null
  selectedRoute.value = null
  approval.value = null
  evidence.value = null
  statsBefore.value = null
  statsAfter.value = null
  localError.value = ''
  lastAction.value = 'Waiting for a classified document'
}
</script>

<template>
  <div>
    <!-- Step indicator -->
    <div class="step-indicator">
      <div class="step" :class="{ 'step--done': step > 1, 'step--active': step === 1 }">
        <span class="step__num">1</span>
        <span class="step__label">Input</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 2, 'step--active': step === 2 }">
        <span class="step__num">2</span>
        <span class="step__label">Governance</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 3, 'step--active': step === 3 }">
        <span class="step__num">3</span>
        <span class="step__label">Model result</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step > 4, 'step--active': step === 4 }">
        <span class="step__num">4</span>
        <span class="step__label">Authority</span>
      </div>
      <div class="step__connector" />
      <div class="step" :class="{ 'step--done': step >= 5, 'step--active': step === 5 }">
        <span class="step__num">5</span>
        <span class="step__label">Evidence</span>
      </div>
    </div>

    <!-- Row 1: Input + Governance -->
    <div class="grid-doc">
      <!-- 01 · INPUT -->
      <div class="panel">
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

        <!-- Trusted metadata cells -->
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

        <p class="microcopy">
          Trusted metadata is read server-side before inference.
          This UI displays backend evidence; it does not classify documents.
        </p>
      </div>

      <!-- 02 · GOVERNANCE DECISION -->
      <div class="panel">
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

        <!-- Legacy route note — never relabel as NVIDIA -->
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
            <span class="pill pill--neutral">
              {{ Math.round((assessment.confidence ?? 0) * 100) }}% confidence
            </span>
          </div>
          <p class="assessment-rationale">{{ assessment.rationale }}</p>
        </div>
        <div v-else class="empty-state">
          Nemotron's typed assessment appears here after an allowed inference.
        </div>
      </div>

      <!-- 04 · CONSEQUENTIAL ACTION -->
      <div class="panel" :class="{ 'panel--accent': approval }">
        <span class="eyebrow">04 · Consequential action</span>
        <template v-if="approval">
          <div class="panel-title" style="margin-top:10px; font-size:1.05rem;">
            Human authority required
          </div>
          <p class="authority-copy" style="margin-top:8px;">
            The model proposed
            <span class="authority-tool">{{ approval.toolName }}</span>
            TramAI suspended execution before the side effect.
            Payment count right now:
            <strong>{{ statsAfter?.paymentExecutionCount ?? '—' }}</strong>
          </p>
          <div class="authority-actions">
            <button class="btn btn--primary" :disabled="busy" @click="approvePayment">
              <span v-if="busy" class="spinner" />
              Approve
            </button>
            <button class="btn btn--danger" :disabled="busy" @click="denyPayment">
              Deny
            </button>
          </div>
          <div class="approval-id">Approval ID: {{ approval.approvalId }}</div>
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

        <div
          class="audit-state"
          :class="{
            'audit-state--valid':   evidence?.chainValid === true,
            'audit-state--invalid': evidence?.chainValid === false,
          }"
        >
          <span class="audit-label">Audit chain</span>
          <span class="audit-value">
            {{ evidence?.chainValid === true ? 'VALID'
               : evidence?.chainValid === false ? 'INVALID'
               : '—' }}
          </span>
        </div>

        <p class="microcopy">
          Evidence is fetched from the backend after approval/denial.
          Chain validity comes from hash-chained server records.
        </p>
      </div>
    </div>

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
