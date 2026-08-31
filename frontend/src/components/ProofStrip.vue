<script setup>
import { computed } from 'vue'
import { routeToBoundary, counterDelta } from '../model.js'

const props = defineProps({
  metadata:      { type: Object, default: null },
  selectedRoute: { type: String, default: null },
  assessment:    { type: Object, default: null },
  approval:      { type: Object, default: null },
  evidence:      { type: Object, default: null },
  statsBefore:   { type: Object, default: null },
  statsAfter:    { type: Object, default: null },
  replayResult:  { type: Object, default: null },
  denialResult:  { type: Object, default: null },
})

const show = computed(() =>
  props.metadata || props.assessment || props.approval || props.evidence
)
const selectedBoundary = computed(() => routeToBoundary(props.selectedRoute))
const paymentBefore    = computed(() => props.statsBefore?.paymentExecutionCount ?? null)
const paymentAfter     = computed(() => props.statsAfter?.paymentExecutionCount  ?? null)
const denialDelta = computed(() => {
  if (!props.denialResult) return null
  return (props.denialResult.after?.globalNvidiaInvocationCount ?? 0) -
         (props.denialResult.before?.globalNvidiaInvocationCount ?? 0)
})
</script>

<template>
  <div v-if="show" class="proof-strip">
    <div class="proof-cell">
      <span class="proof-cell__label">METADATA</span>
      <span class="proof-cell__value font-mono" v-if="metadata">{{ metadata.classification }}<br/>{{ metadata.residency }}</span>
      <span class="proof-cell__value" v-else>—</span>
    </div>
    <div class="proof-sep">→</div>
    <div class="proof-cell">
      <span class="proof-cell__label">POLICY</span>
      <span class="proof-cell__value">
        <span v-if="selectedBoundary" class="pill pill--selected" style="font-size:9px;">{{ selectedRoute }}</span>
        <span v-else-if="selectedRoute" class="pill pill--neutral" style="font-size:9px;">{{ selectedRoute }}</span>
        <span v-else>—</span>
      </span>
    </div>
    <div class="proof-sep">→</div>
    <div class="proof-cell" :class="{ 'proof-cell--active': denialResult }">
      <span class="proof-cell__label">DENIED</span>
      <span class="proof-cell__value">
        <template v-if="denialResult && !denialResult.unexpectedSuccess">
          <span class="pill pill--denied" style="font-size:9px;">GLOBAL</span>
          <span style="font-size:10px;color:var(--muted);margin-left:4px;">Δ={{ denialDelta === 0 ? '0 ✓' : denialDelta }}</span>
        </template>
        <span v-else>—</span>
      </span>
    </div>
    <div class="proof-sep">→</div>
    <div class="proof-cell">
      <span class="proof-cell__label">NVIDIA EXEC</span>
      <span class="proof-cell__value">
        <span v-if="selectedBoundary" class="pill pill--allowed" style="font-size:9px;">{{ selectedBoundary }}</span>
        <span v-else>—</span>
      </span>
    </div>
    <div class="proof-sep">→</div>
    <div class="proof-cell" :class="{ 'proof-cell--active': paymentAfter !== null && paymentBefore !== null }">
      <span class="proof-cell__label">PAYMENTS</span>
      <span class="proof-cell__value font-mono">
        <template v-if="paymentBefore !== null && paymentAfter !== null">{{ paymentBefore }} → {{ paymentAfter }}</template>
        <span v-else>—</span>
      </span>
    </div>
    <div class="proof-sep">→</div>
    <div class="proof-cell" :class="{ 'proof-cell--active': evidence }">
      <span class="proof-cell__label">AUDIT</span>
      <span class="proof-cell__value">
        <template v-if="evidence">
          <span class="pill" :class="evidence.chainValid ? 'pill--allowed' : 'pill--denied'" style="font-size:9px;">{{ evidence.chainValid ? 'VALID' : 'INVALID' }}</span>
          <span style="font-size:10px;color:var(--muted);margin-left:4px;">{{ evidence.eventCount }}ev</span>
        </template>
        <span v-else>—</span>
      </span>
    </div>
    <template v-if="replayResult">
      <div class="proof-sep">→</div>
      <div class="proof-cell" :class="{ 'proof-cell--active': replayResult.rejected }">
        <span class="proof-cell__label">REPLAY</span>
        <span class="proof-cell__value">
          <span v-if="replayResult.rejected" class="pill pill--allowed" style="font-size:9px;">REJECTED ✓</span>
          <span v-else class="pill pill--denied" style="font-size:9px;">NOT REJECTED</span>
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.proof-strip {
  display: flex; align-items: center; flex-wrap: nowrap; gap: 0; overflow-x: auto;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-md);
  background: linear-gradient(90deg, rgba(16,26,14,0.97), rgba(10,16,10,0.96));
  padding: 14px 18px; margin-top: 22px;
  box-shadow: 0 0 32px rgba(118,185,0,0.07);
}
.proof-cell { display: flex; flex-direction: column; gap: 5px; flex-shrink: 0; min-width: 80px; }
.proof-cell--active .proof-cell__label { color: var(--accent-bright); }
.proof-cell__label { font-size: 8.5px; font-weight: 800; letter-spacing: 0.14em; text-transform: uppercase; color: var(--muted); }
.proof-cell__value { font-size: 11.5px; font-weight: 700; color: var(--text-bright); line-height: 1.4; }
.proof-sep { color: var(--line-mid); font-size: 16px; font-weight: 300; padding: 0 12px; flex-shrink: 0; align-self: center; }
</style>
