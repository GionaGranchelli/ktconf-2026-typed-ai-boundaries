<script setup>
import { computed, onMounted, ref } from 'vue'
import BoundaryCard from './components/BoundaryCard.vue'
import { analyzePdf, approve, deny, getEvidence, getStats, health } from './api'
import { allowedBoundaries, boundaries, counterDelta, formatMoney, routeToBoundary } from './model'

const backend = ref('checking')
const file = ref(null)
const dragging = ref(false)
const busy = ref(false)
const error = ref('')
const metadata = ref(null)
const assessment = ref(null)
const selectedRoute = ref(null)
const approval = ref(null)
const evidence = ref(null)
const statsBefore = ref(null)
const statsAfter = ref(null)
const lastAction = ref('Waiting for a document')

const allowed = computed(() => allowedBoundaries(metadata.value))
const selectedBoundary = computed(() => routeToBoundary(selectedRoute.value))
const amount = computed(() => assessment.value ? formatMoney(assessment.value.amountCents, assessment.value.currency) : '—')
const hasRun = computed(() => Boolean(metadata.value || assessment.value || approval.value))

const providerDeltas = computed(() => ({
  LOCAL_NVIDIA: counterDelta(statsBefore.value, statsAfter.value, 'localNvidiaInvocationCount'),
  EU_CLOUD: counterDelta(statsBefore.value, statsAfter.value, 'euNvidiaInvocationCount'),
  GLOBAL_CLOUD: counterDelta(statsBefore.value, statsAfter.value, 'globalNvidiaInvocationCount'),
}))

onMounted(async () => {
  try {
    await health()
    backend.value = 'online'
    statsAfter.value = await getStats()
  } catch {
    backend.value = 'offline'
  }
})

function selectFiles(files) {
  const candidate = files?.[0]
  if (!candidate) return
  if (candidate.type !== 'application/pdf' && !candidate.name.toLowerCase().endsWith('.pdf')) {
    error.value = 'Choose a PDF document.'
    return
  }
  file.value = candidate
  error.value = ''
}

function onDrop(event) {
  dragging.value = false
  selectFiles(event.dataTransfer.files)
}

async function processDocument() {
  if (!file.value || busy.value) return
  busy.value = true
  error.value = ''
  metadata.value = null
  assessment.value = null
  selectedRoute.value = null
  approval.value = null
  evidence.value = null
  lastAction.value = 'Reading trusted metadata and evaluating policy'

  try {
    statsBefore.value = await getStats()
    const result = await analyzePdf(file.value)

    metadata.value = result.metadata || null
    selectedRoute.value = result.selectedRoute || null
    assessment.value = result.assessment || null

    if (result.approvalId) {
      approval.value = result
      lastAction.value = 'Model finished reasoning · runtime is awaiting human authority'
    } else {
      lastAction.value = selectedRoute.value
        ? `Backend selected ${selectedRoute.value}`
        : 'Analysis completed'
    }

    statsAfter.value = await getStats()
  } catch (e) {
    error.value = e.message || 'Document processing failed.'
    lastAction.value = 'Request denied or rejected'
    try { statsAfter.value = await getStats() } catch { /* preserve original error */ }
  } finally {
    busy.value = false
  }
}

async function approvePayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  error.value = ''
  try {
    assessment.value = await approve(approval.value.approvalId)
    statsAfter.value = await getStats()
    evidence.value = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human approved · workflow resumed'
  } catch (e) {
    error.value = e.message || 'Approval failed.'
  } finally {
    busy.value = false
  }
}

async function denyPayment() {
  if (!approval.value?.approvalId || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await deny(approval.value.approvalId)
    statsAfter.value = await getStats()
    evidence.value = await getEvidence(approval.value.approvalId)
    lastAction.value = 'Human denied · side effect remains blocked'
  } catch (e) {
    error.value = e.message || 'Denial failed.'
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
  error.value = ''
  lastAction.value = 'Waiting for a document'
}
</script>

<template>
  <main class="shell">
    <header class="hero">
      <div>
        <div class="hero__kicker"><span class="signal-dot"></span> NVIDIA GTC BERLIN · GOVERNANCE CONSOLE</div>
        <h1>The Model Is Not the Authority</h1>
        <p>One TramAI policy plane governs where NVIDIA inference may run and what the model may actually do.</p>
      </div>
      <div class="runtime-status" :class="`runtime-status--${backend}`">
        <span class="runtime-status__dot"></span>
        Backend {{ backend }}
      </div>
    </header>

    <section class="workspace">
      <div class="panel document-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">01 · INPUT</span>
            <h2>Classified document</h2>
          </div>
          <button v-if="hasRun || file" class="text-button" @click="reset">Reset</button>
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
          <strong>{{ file ? file.name : 'Drop a classified PDF' }}</strong>
          <span>{{ file ? `${Math.ceil(file.size / 1024)} KB · ready` : 'or click to choose a file' }}</span>
        </label>

        <button class="primary-button" :disabled="!file || busy" @click="processDocument">
          {{ busy ? 'Evaluating…' : 'Process under policy' }}
        </button>

        <div class="metadata-grid">
          <div>
            <span>Trusted classification</span>
            <strong>{{ metadata?.classification || '—' }}</strong>
          </div>
          <div>
            <span>Residency</span>
            <strong>{{ metadata?.residency || '—' }}</strong>
          </div>
        </div>
        <p class="microcopy">Trusted metadata is parsed locally before model inference. The UI displays backend evidence; it does not classify the document itself.</p>
      </div>

      <div class="panel decision-panel">
        <div class="panel-heading">
          <div>
            <span class="eyebrow">02 · AUTHORITY</span>
            <h2>Governance decision</h2>
          </div>
          <span class="decision-state" :class="{ 'decision-state--live': hasRun }">{{ lastAction }}</span>
        </div>

        <div class="boundary-grid">
          <BoundaryCard
            v-for="boundary in boundaries"
            :key="boundary.id"
            :boundary="boundary"
            :allowed="allowed.has(boundary.id)"
            :selected="selectedBoundary === boundary.id"
            :delta="providerDeltas[boundary.id]"
          />
        </div>

        <div v-if="selectedRoute && !selectedBoundary" class="legacy-route-note">
          Current backend route: <strong>{{ selectedRoute }}</strong>. The three-NVIDIA-boundary integration is completed by task 006; this console never relabels a legacy route as NVIDIA.
        </div>
      </div>
    </section>

    <section class="lower-grid">
      <div class="panel result-panel">
        <span class="eyebrow">03 · MODEL RESULT</span>
        <div v-if="assessment" class="assessment">
          <div class="assessment__amount">{{ amount }}</div>
          <div class="assessment__supplier">{{ assessment.supplierName }}</div>
          <div class="assessment__tags">
            <span class="pill">{{ assessment.risk }}</span>
            <span class="pill">{{ assessment.recommendedAction }}</span>
            <span class="pill">{{ Math.round((assessment.confidence || 0) * 100) }}% confidence</span>
          </div>
          <p>{{ assessment.rationale }}</p>
        </div>
        <div v-else class="empty-state">Nemotron's typed assessment will appear here after an allowed inference.</div>
      </div>

      <div class="panel authority-panel">
        <span class="eyebrow">04 · CONSEQUENTIAL ACTION</span>
        <template v-if="approval">
          <h2>Human authority required</h2>
          <p class="authority-copy">The model proposed <strong>{{ approval.toolName }}</strong>. TramAI suspended execution before the side effect.</p>
          <div class="approval-actions">
            <button class="primary-button" :disabled="busy" @click="approvePayment">Approve</button>
            <button class="danger-button" :disabled="busy" @click="denyPayment">Deny</button>
          </div>
          <div class="approval-id">Approval {{ approval.approvalId }}</div>
        </template>
        <div v-else class="empty-state">A HIGH-risk tool request will pause here instead of executing automatically.</div>
      </div>

      <div class="panel evidence-panel">
        <span class="eyebrow">05 · EVIDENCE</span>
        <div class="metrics">
          <div><span>Local NVIDIA calls</span><strong>{{ statsAfter?.localNvidiaInvocationCount ?? '—' }}</strong></div>
          <div><span>EU NVIDIA calls</span><strong>{{ statsAfter?.euNvidiaInvocationCount ?? '—' }}</strong></div>
          <div><span>Global NVIDIA calls</span><strong>{{ statsAfter?.globalNvidiaInvocationCount ?? '—' }}</strong></div>
          <div><span>Payments executed</span><strong>{{ statsAfter?.paymentExecutionCount ?? '—' }}</strong></div>
        </div>
        <div class="audit-state" :class="{ 'audit-state--valid': evidence?.chainValid === true }">
          <span>Audit chain</span>
          <strong>{{ evidence?.chainValid === true ? 'VALID' : evidence ? 'CHECK' : '—' }}</strong>
        </div>
      </div>
    </section>

    <div v-if="error" class="error-banner" role="alert">
      <strong>Policy / request result</strong>
      <span>{{ error }}</span>
    </div>
  </main>
</template>
