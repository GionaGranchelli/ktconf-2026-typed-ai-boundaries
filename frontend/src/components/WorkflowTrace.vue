<!--
  WorkflowTrace.vue — Live visualization of the AI governance workflow.

  Steps are reconstructed from the reactive state props that DocumentFlowPage
  already tracks. No extra backend endpoint is needed — the data we have is:

  Step 1 — PDF Upload       : file ref (client-only)
  Step 2 — Metadata Read    : metadata (from 200/202 response)
  Step 3 — TramAI Policy    : allowedSet (derived) + selectedRoute (backend)
  Step 4 — Model Inference  : assessment (from 200 response)
  Step 5 — Tool Intercept   : approval (from 202 response, when HIGH-risk)
  Step 6 — Human Decision   : evidence (after approve/deny)
  Step 7 — Flow Complete    : typed result with no pending governed action,
                             or verified evidence after human decision

  The component is purely presentational — parent keeps all mutable state.
-->
<script setup>
import { computed } from 'vue'
import { allowedBoundaries, boundaries, routeToBoundary } from '../model.js'

const props = defineProps({
  file:           { type: Object,  default: null },  // File | null
  busy:           { type: Boolean, default: false },
  metadata:       { type: Object,  default: null },
  selectedRoute:  { type: String,  default: null },
  assessment:     { type: Object,  default: null },
  approval:       { type: Object,  default: null },
  evidence:       { type: Object,  default: null },
  statsBefore:    { type: Object,  default: null },
  statsAfter:     { type: Object,  default: null },
})

// ── Derived ────────────────────────────────────────────────────
const allowedSet       = computed(() => allowedBoundaries(props.metadata))
const selectedBoundary = computed(() => routeToBoundary(props.selectedRoute))
const pendingHighRiskReview = computed(() =>
  Boolean(props.assessment?.risk === 'HIGH' && !props.approval && !props.evidence))
const pendingAutomaticPayment = computed(() =>
  Boolean(props.assessment?.risk === 'LOW' && props.assessment?.recommendedAction !== 'SCHEDULE_PAYMENT' && !props.approval && !props.evidence))

// Which step are we actively running? (for the spinner)
const activeStep = computed(() => {
  if (props.evidence)   return 7
  if (props.assessment && !props.approval && !props.busy && !pendingHighRiskReview.value && !pendingAutomaticPayment.value) return 7
  if (props.approval)   return 5
  if (props.assessment) return 4
  if (props.metadata)   return 3
  if (props.busy)       return 2
  if (props.file)       return 1
  return 0
})

// Provider call deltas keyed by boundary id
const deltas = computed(() => {
  const result = {}
  for (const b of boundaries) {
    const before = props.statsBefore?.[b.statsKey] ?? 0
    const after  = props.statsAfter?.[b.statsKey]  ?? 0
    result[b.id] = after - before
  }
  return result
})

// The provider actually used, for step 4 label
const providerLabel = computed(() => {
  const route = props.selectedRoute || (props.approval?.selectedRoute)
  const b = boundaries.find(b => b.id === route)
  if (b) return b.stack[0]
  if (route) return route
  return null
})

// Total NVIDIA delta for display
const totalDelta = computed(() =>
  Object.values(deltas.value).reduce((a, v) => a + v, 0)
)

// Step status helper
function stepStatus(n) {
  if (activeStep.value === 0) return 'idle'
  if (n < activeStep.value)   return 'done'
  if (n === activeStep.value) {
    if (n === 5 && props.approval) return 'suspended'
    if (n === 7) return 'done'
    if (props.busy)                return 'running'
    return 'active'
  }
  return 'pending'
}
</script>

<template>
  <div class="wf-trace" v-if="activeStep > 0">
    <div class="wf-trace__header">
      <span class="eyebrow">Workflow trace</span>
      <span v-if="totalDelta > 0" class="wf-delta">
        +{{ totalDelta }} provider call{{ totalDelta !== 1 ? 's' : '' }}
      </span>
    </div>

    <div class="wf-steps">

      <!-- ── Step 1: PDF Upload ─────────────────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(1)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <svg viewBox="0 0 10 10" fill="none"><path d="M2 1h4l2 2v6H2V1z" stroke="currentColor" stroke-width="1.1"/></svg>
          </div>
          <div class="wf-step__line" />
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">PDF Upload</span>
            <span v-if="file" class="wf-step__detail font-mono">{{ file.name }}</span>
            <span class="wf-step__tag wf-step__tag--neutral">
              {{ file ? `${Math.ceil(file.size / 1024)} KB` : '—' }}
            </span>
          </div>
          <div v-if="file" class="wf-step__note">
            Client-side only — file sent to backend for metadata extraction
          </div>
        </div>
      </div>

      <!-- ── Step 2: Metadata Extraction ───────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(2)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <svg viewBox="0 0 10 10" fill="none"><circle cx="5" cy="5" r="3" stroke="currentColor" stroke-width="1.1"/><path d="M5 3.5v2l1 1" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/></svg>
          </div>
          <div class="wf-step__line" />
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">Metadata extraction</span>
            <template v-if="metadata">
              <span class="wf-step__detail font-mono">
                {{ metadata.classification }} / {{ metadata.residency }}
              </span>
            </template>
            <span v-if="stepStatus(2) === 'running'" class="spinner" />
            <span class="wf-step__tag wf-step__tag--info">LOCAL · no model call</span>
          </div>
          <div v-if="metadata" class="wf-step__note">
            Trusted metadata read from PDF before any provider is invoked
          </div>
        </div>
      </div>

      <!-- ── Step 3: TramAI Policy Check ───────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(3)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <svg viewBox="0 0 10 10" fill="none"><path d="M5 1 1.5 2.5v3C1.5 7.7 3.2 9 5 9.5 6.8 9 8.5 7.7 8.5 5.5v-3L5 1z" stroke="currentColor" stroke-width="1.1"/></svg>
          </div>
          <div class="wf-step__line" />
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">TramAI policy</span>
            <template v-if="metadata">
              <span
                v-for="b in boundaries"
                :key="b.id"
                class="wf-step__tag"
                :class="allowedSet.has(b.id) ? 'wf-step__tag--allowed' : 'wf-step__tag--denied'"
              >
                {{ b.title }} {{ allowedSet.has(b.id) ? '✓' : '✕' }}
              </span>
            </template>
            <span v-else class="wf-step__tag wf-step__tag--neutral">—</span>
          </div>
          <div v-if="selectedRoute" class="wf-step__note">
            Backend selected:
            <span class="font-mono" style="color:var(--text-bright)">{{ selectedRoute }}</span>
            <template v-if="!selectedBoundary">
              <span class="wf-step__tag wf-step__tag--warn" style="margin-left:6px">legacy route</span>
            </template>
          </div>
        </div>
      </div>

      <!-- ── Step 4: Model Inference ────────────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(4)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <!-- brain/chip icon -->
            <svg viewBox="0 0 10 10" fill="none"><rect x="2" y="2" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.1"/><path d="M3.5 4h3M3.5 6h2" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/></svg>
          </div>
          <div class="wf-step__line" />
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">Model inference</span>
            <span v-if="providerLabel" class="wf-step__detail font-mono">{{ providerLabel }}</span>
            <span v-if="stepStatus(4) === 'running'" class="spinner" />
            <template v-if="assessment">
              <span class="wf-step__tag wf-step__tag--neutral">assess</span>
              <span
                class="wf-step__tag"
                :class="assessment.risk === 'HIGH' ? 'wf-step__tag--denied' : 'wf-step__tag--allowed'"
              >{{ assessment.risk }}</span>
            </template>
          </div>
          <div v-if="assessment" class="wf-step__note">
            {{ Math.round((assessment.confidence ?? 0) * 100) }}% confidence ·
            {{ assessment.recommendedAction }}
          </div>
          <!-- Provider call deltas -->
          <div v-if="statsAfter && statsBefore" class="wf-deltas">
            <span
              v-for="b in boundaries"
              :key="b.id"
              class="wf-delta-pill"
              :class="{ 'wf-delta-pill--active': deltas[b.id] > 0 }"
            >
              {{ b.title }} {{ deltas[b.id] !== undefined ? (deltas[b.id] > 0 ? `+${deltas[b.id]}` : deltas[b.id]) : '—' }}
            </span>
          </div>
        </div>
      </div>

      <!-- ── Step 5: Tool Intercept ─────────────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(5)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <!-- warning/intercept -->
            <svg viewBox="0 0 10 10" fill="none"><path d="M5 1.5 1 8.5h8L5 1.5z" stroke="currentColor" stroke-width="1.1" stroke-linejoin="round"/><path d="M5 4v2M5 7.2v.3" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/></svg>
          </div>
          <div class="wf-step__line" />
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">Tool intercept</span>
            <template v-if="approval">
              <span class="wf-step__detail font-mono">{{ approval.toolName }}</span>
              <span class="wf-step__tag wf-step__tag--suspended">SUSPENDED</span>
            </template>
            <template v-else-if="assessment && assessment.risk !== 'HIGH'">
              <span class="wf-step__tag wf-step__tag--allowed">no tool call</span>
            </template>
            <template v-else>
              <span class="wf-step__tag wf-step__tag--neutral">—</span>
            </template>
          </div>
          <div v-if="approval" class="wf-step__note">
            {{ approval.rationale || 'High-risk side effect suspended by TramAI before execution.' }}
          </div>
          <div v-else-if="assessment && assessment.risk !== 'HIGH'" class="wf-step__note">
            Risk is {{ assessment.risk }} — no tool suspension required
          </div>
        </div>
      </div>

      <!-- ── Step 6: Human Decision ─────────────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(6)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <!-- person icon -->
            <svg viewBox="0 0 10 10" fill="none"><circle cx="5" cy="3" r="1.5" stroke="currentColor" stroke-width="1.1"/><path d="M2 8.5c0-1.7 1.3-3 3-3s3 1.3 3 3" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/></svg>
          </div>
          <!-- no line after last step -->
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">Human decision</span>
            <template v-if="evidence">
              <span class="wf-step__tag wf-step__tag--allowed">COMPLETE</span>
              <span
                class="wf-step__tag"
                :class="evidence.chainValid ? 'wf-step__tag--allowed' : 'wf-step__tag--denied'"
              >
                chain {{ evidence.chainValid ? 'VALID' : 'INVALID' }}
              </span>
            </template>
            <template v-else-if="approval">
              <span class="wf-step__tag wf-step__tag--suspended">awaiting</span>
            </template>
            <template v-else-if="assessment">
              <span class="wf-step__tag wf-step__tag--neutral">NOT REQUIRED</span>
            </template>
            <template v-else>
              <span class="wf-step__tag wf-step__tag--neutral">—</span>
            </template>
          </div>
          <div v-if="evidence" class="wf-step__note">
            {{ evidence.eventCount }} audit events · payment count locked
          </div>
          <div v-else-if="approval" class="wf-step__note">
            Approve or deny in the Consequential Action panel →
          </div>
          <div v-else-if="assessment" class="wf-step__note">
            No human decision required for this typed assessment.
          </div>
          <div v-else class="wf-step__note" style="opacity:0.45">
            Pending tool suspension
          </div>
        </div>
      </div>

      <!-- ── Step 7: Flow Complete ──────────────────────────── -->
      <div class="wf-step" :class="`wf-step--${stepStatus(7)}`">
        <div class="wf-step__spine">
          <div class="wf-step__node">
            <svg viewBox="0 0 10 10" fill="none"><path d="m2 5 2 2 4-4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </div>
        </div>
        <div class="wf-step__body">
          <div class="wf-step__row">
            <span class="wf-step__name">Flow complete</span>
            <span v-if="assessment && !approval && !pendingHighRiskReview && !pendingAutomaticPayment" class="wf-step__detail">typed result returned</span>
            <span v-else-if="evidence" class="wf-step__detail">governed action verified</span>
            <span v-if="pendingHighRiskReview" class="wf-step__tag wf-step__tag--suspended">REVIEW REQUIRED</span>
            <span v-else-if="pendingAutomaticPayment" class="wf-step__tag wf-step__tag--suspended">AUTO PAYMENT PENDING</span>
            <span v-else class="wf-step__tag wf-step__tag--allowed">COMPLETE</span>
          </div>
          <div v-if="pendingHighRiskReview" class="wf-step__note">
            High-risk assessment returned without a governed payment suspension; no payment was executed.
          </div>
          <div v-else-if="pendingAutomaticPayment" class="wf-step__note">
            LOW-risk assessment returned without the auto-schedule-payment tool; no payment was executed.
          </div>
          <div v-else-if="assessment && !approval" class="wf-step__note">
            {{ selectedRoute }} completed with no pending tool or human decision.
          </div>
          <div v-else-if="evidence" class="wf-step__note">
            TramAI continuation completed · audit chain {{ evidence.chainValid ? 'VALID' : 'INVALID' }}
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ── Container ────────────────────────────────────────────── */
.wf-trace {
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: linear-gradient(160deg, rgba(14,20,14,0.94), rgba(8,12,8,0.92));
  padding: 18px 20px 12px;
}

.wf-trace__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.wf-delta {
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-bright);
  background: var(--accent-dim);
  border: 1px solid var(--line-mid);
  border-radius: var(--r-pill);
  padding: 2px 10px;
  font-family: var(--font-mono);
  letter-spacing: 0.04em;
}

/* ── Step list ─────────────────────────────────────────────── */
.wf-steps {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ── Single step ───────────────────────────────────────────── */
.wf-step {
  display: flex;
  gap: 14px;
  min-height: 48px;
}

/* Spine: node + connecting line */
.wf-step__spine {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 22px;
}

.wf-step__node {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px solid var(--line-mid);
  background: var(--bg-raised);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  transition: border-color 0.2s, background 0.2s;
  color: var(--muted);
}

.wf-step__node svg {
  width: 10px;
  height: 10px;
}

.wf-step__line {
  flex: 1;
  width: 1.5px;
  background: var(--line);
  margin: 3px 0;
  min-height: 14px;
  transition: background 0.2s;
}

/* Body */
.wf-step__body {
  flex: 1;
  padding-bottom: 14px;
  min-width: 0;
}

.wf-step__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  min-height: 22px;
}

.wf-step__name {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--muted-light);
  flex-shrink: 0;
}

.wf-step__detail {
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.wf-step__note {
  font-size: 10.5px;
  color: var(--muted);
  line-height: 1.5;
  margin-top: 4px;
}

/* ── Step tags ────────────────────────────────────────────── */
.wf-step__tag {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  border-radius: var(--r-pill);
  padding: 2px 8px;
  border: 1px solid var(--line);
  white-space: nowrap;
  color: var(--muted);
}

.wf-step__tag--allowed  { color: #82c982; border-color: rgba(130,201,130,0.3); background: rgba(130,201,130,0.07); }
.wf-step__tag--denied   { color: #f07070; border-color: var(--danger-border);  background: var(--danger-dim); }
.wf-step__tag--suspended{ color: var(--warning); border-color: rgba(245,166,35,0.35); background: rgba(245,166,35,0.08); }
.wf-step__tag--warn     { color: var(--warning); border-color: rgba(245,166,35,0.35); background: rgba(245,166,35,0.08); }
.wf-step__tag--info     { color: var(--info, #4eb3e8); border-color: rgba(78,179,232,0.28); background: rgba(78,179,232,0.06); }
.wf-step__tag--neutral  { color: var(--muted-light); }

/* ── Status variants ──────────────────────────────────────── */

/* done */
.wf-step--done .wf-step__node {
  border-color: var(--accent);
  background: var(--accent-dim);
  color: var(--accent-bright);
}
.wf-step--done .wf-step__line { background: var(--line-mid); }
.wf-step--done .wf-step__name { color: var(--text-bright); }

/* active / running */
.wf-step--active .wf-step__node,
.wf-step--running .wf-step__node {
  border-color: var(--accent-bright);
  background: var(--accent-dim);
  color: var(--accent-bright);
  box-shadow: 0 0 10px rgba(118,185,0,0.3);
  animation: nodePulse 1.6s ease infinite;
}
.wf-step--active .wf-step__name,
.wf-step--running .wf-step__name { color: var(--text-bright); }

/* suspended */
.wf-step--suspended .wf-step__node {
  border-color: rgba(245,166,35,0.55);
  background: rgba(245,166,35,0.10);
  color: var(--warning);
}
.wf-step--suspended .wf-step__line { background: rgba(245,166,35,0.28); }
.wf-step--suspended .wf-step__name { color: var(--warning); }

/* pending */
.wf-step--pending .wf-step__node { opacity: 0.38; }
.wf-step--pending .wf-step__name { opacity: 0.38; }
.wf-step--pending .wf-step__row  { opacity: 0.5; }

/* idle */
.wf-step--idle { display: none; }

@keyframes nodePulse {
  0%, 100% { box-shadow: 0 0 8px rgba(118,185,0,0.28); }
  50%       { box-shadow: 0 0 16px rgba(118,185,0,0.52); }
}

/* ── Provider deltas row ──────────────────────────────────── */
.wf-deltas {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}

.wf-delta-pill {
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--r-pill);
  border: 1px solid var(--line);
  color: var(--muted);
  letter-spacing: 0.04em;
  transition: color 0.2s, border-color 0.2s;
}

.wf-delta-pill--active {
  color: var(--accent-bright);
  border-color: var(--line-mid);
  background: var(--accent-dim);
}
</style>
