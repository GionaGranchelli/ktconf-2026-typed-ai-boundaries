<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDocumentHistory, getDocumentHistoryRecord } from '../api.js'
import { formatMoney } from '../model.js'

const emit = defineEmits(['navigate-home'])
const records = ref([])
const selected = ref(null)
const loading = ref(true)
const error = ref('')
const timelineEvents = computed(() => [
  ...(selected.value?.workflowEvents || []).map(event => ({ ...event, kind: 'workflow' })),
  ...(selected.value?.auditEvents || []).map(event => ({ ...event, kind: 'audit' })),
].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()))

async function load() {
  loading.value = true
  error.value = ''
  try {
    records.value = await getDocumentHistory()
    if (selected.value) selected.value = records.value.find(r => r.id === selected.value.id) || selected.value
  } catch (e) { error.value = e.message || 'Document history unavailable.' }
  finally { loading.value = false }
}
async function select(record) {
  selected.value = record
  try { selected.value = await getDocumentHistoryRecord(record.id) }
  catch (e) { error.value = e.message || 'Document detail unavailable.' }
}
function label(event) {
  return { APPROVAL_SUSPENDED: 'Approval suspended', BEFORE_WORKFLOW_RESUME: 'Continuation prepared', APPROVAL_RESUMED: 'Workflow resumed', APPROVAL_COMPLETED: 'Payment completed' }[event?.enforcementPoint] || event?.enforcementPoint || 'Audit event'
}
function details(event) {
  const parts = []
  if (event?.metadata?.toolName) parts.push(`tool: ${event.metadata.toolName}`)
  if (event?.decision) parts.push(String(event.decision).replaceAll('_', ' '))
  if (event?.actor) parts.push(`by ${event.actor}`)
  if (event?.reasonCode) parts.push(String(event.reasonCode).replaceAll('_', ' '))
  return parts.join(' · ')
}
function date(value) { return value ? new Date(value).toLocaleString() : '—' }
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading"><div><span class="eyebrow-label">Governed document history</span><h2 class="page-title">Every upload. Every decision.</h2><p class="page-lede">Backend-owned records for this demo session. History resets when Spring restarts.</p></div><div class="page-heading__actions"><button class="btn btn--ghost btn--sm" @click="emit('navigate-home')">Back to live governance</button><button class="btn btn--ghost btn--sm" :disabled="loading" @click="load">Refresh</button></div></div>
    <div v-if="error" class="alert-banner alert-banner--error">{{ error }}</div>
    <div v-if="loading" class="panel history-empty"><span class="spinner" /> Loading governed history…</div>
    <div v-else-if="!records.length" class="panel history-empty">No PDFs have been uploaded in this backend session yet.</div>
    <div v-else class="history-layout">
      <div class="panel history-list"><div class="panel-heading"><div><span class="eyebrow">Documents</span><div class="panel-title">{{ records.length }} recorded upload{{ records.length === 1 ? '' : 's' }}</div></div></div><button v-for="record in records" :key="record.id" class="history-row" :class="{ active: selected?.id === record.id }" @click="select(record)"><span class="history-row__marker" /><span class="history-row__main"><strong>{{ record.invoice.invoiceId }}</strong><small>{{ record.metadata.classification }} · {{ record.metadata.residency }}</small><small>{{ date(record.recordedAt) }}</small></span><span class="history-row__status" :class="`status--${record.status.toLowerCase()}`">{{ record.status.replaceAll('_', ' ') }}</span></button></div>
      <div v-if="selected" class="history-detail">
        <div class="panel"><div class="panel-heading"><div><span class="eyebrow">Document detail</span><div class="panel-title">{{ selected.invoice.invoiceId }}</div><div class="panel-subtitle">Recorded {{ date(selected.recordedAt) }}</div></div><span class="pill" :class="selected.status === 'DENIED' ? 'pill--denied' : 'pill--allowed'">{{ selected.status.replaceAll('_', ' ') }}</span></div><div class="history-facts"><div><small>CLASSIFICATION</small><strong>{{ selected.metadata.classification }}</strong></div><div><small>RESIDENCY</small><strong>{{ selected.metadata.residency }}</strong></div><div><small>ROUTE</small><strong>{{ selected.selectedRoute }}</strong></div><div><small>SOURCE</small><strong>{{ selected.classificationSource }}</strong></div></div><p v-if="selected.denialReasonCode" class="history-rationale">{{ selected.denialReasonCode }} · provider was not invoked</p></div>
        <div class="panel"><span class="eyebrow">Invoice context</span><div class="history-invoice"><strong>{{ formatMoney(selected.invoice.amountCents, selected.invoice.currency) }}</strong><span>{{ selected.invoice.supplierName }}</span><span>{{ selected.invoice.description }}</span></div></div>
        <div v-if="selected.assessment" class="panel"><span class="eyebrow">Typed assessment</span><div class="history-facts"><div><small>RISK</small><strong>{{ selected.assessment.risk }}</strong></div><div><small>ACTION</small><strong>{{ selected.assessment.recommendedAction }}</strong></div><div><small>CONFIDENCE</small><strong>{{ Math.round(selected.assessment.confidence * 100) }}%</strong></div></div><p class="history-rationale">{{ selected.assessment.rationale }}</p></div>
        <div class="panel"><span class="eyebrow">Governed workflow</span><div class="history-facts"><div><small>TOOL</small><strong>{{ selected.toolName || 'None requested' }}</strong></div><div><small>APPROVAL</small><strong>{{ selected.approvalId ? 'Human gate recorded' : 'Not required' }}</strong></div><div><small>PAYMENT</small><strong>{{ selected.status === 'SCHEDULED' ? 'Scheduled' : selected.status === 'DENIED' ? 'Denied' : 'Not executed' }}</strong></div><div><small>AUDIT</small><strong>{{ selected.auditChainValid === true ? 'Chain valid' : selected.auditChainValid === false ? 'Chain invalid' : 'Pending' }}</strong></div></div><p v-if="selected.rationale" class="history-rationale">{{ selected.rationale }}</p></div>
        <div class="panel"><div class="panel-heading"><div><span class="eyebrow">Timeline</span><div class="panel-title">What happened to this document</div></div><span class="pill pill--neutral">{{ timelineEvents.length }} events</span></div><div v-if="timelineEvents.length" class="history-events"><div v-for="(event, index) in timelineEvents" :key="`${event.kind}-${index}`" class="history-event"><span class="history-event__dot" /><div><strong>{{ event.kind === 'workflow' ? event.label : label(event) }}</strong><small>{{ date(event.timestamp) }}<template v-if="event.kind === 'workflow'"> · {{ event.detail }}</template><template v-else-if="details(event)"> · {{ details(event) }}</template></small><code v-if="event.kind === 'audit'">{{ String(event.eventHash || '').slice(0, 20) }}…</code></div></div></div><div v-else class="empty-state">No workflow events recorded.</div></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px;margin-bottom:22px}.page-heading__actions{display:flex;gap:8px;flex-wrap:wrap}.page-title{margin:6px 0;color:var(--text-bright);font-size:26px;letter-spacing:-.03em}.page-lede{color:var(--muted);font-size:12px}.history-empty{min-height:110px;display:flex;align-items:center;justify-content:center;gap:10px;color:var(--muted-light)}.history-layout{display:grid;grid-template-columns:minmax(280px,.75fr) 1.5fr;gap:14px;align-items:start}.history-list{padding:14px}.history-row{width:100%;display:flex;align-items:center;gap:10px;padding:13px 9px;color:inherit;text-align:left;background:transparent;border:0;border-top:1px solid var(--line);cursor:pointer}.history-row:hover,.history-row.active{background:rgba(118,185,0,.08)}.history-row__marker{width:7px;height:7px;flex-shrink:0;border-radius:50%;background:var(--muted)}.history-row.active .history-row__marker{background:var(--accent);box-shadow:0 0 9px var(--accent-glow)}.history-row__main{display:flex;flex:1;min-width:0;flex-direction:column;gap:4px}.history-row__main strong{font-size:12px;color:var(--text-bright)}.history-row__main small,.history-row__status{font-size:9px;color:var(--muted)}.history-row__status{white-space:nowrap;color:var(--accent-bright);text-transform:uppercase}.status--denied{color:#f08080}.status--awaiting_approval{color:#f1c77b}.history-detail{display:flex;flex-direction:column;gap:14px}.history-facts{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:18px}.history-facts div{display:flex;flex-direction:column;gap:5px;min-width:0}.history-facts small{color:var(--muted);font-size:9px;letter-spacing:.1em}.history-facts strong{color:var(--text-bright);font-size:12px;overflow-wrap:anywhere}.history-invoice{display:flex;flex-direction:column;gap:7px;margin-top:12px;color:var(--muted-light);font-size:12px}.history-invoice strong{color:var(--accent-bright);font:700 25px var(--font-mono)}.history-rationale{margin-top:14px;color:var(--muted-light);font-size:11px;line-height:1.55}.history-events{display:flex;flex-direction:column;gap:0;margin-top:16px}.history-event{display:flex;gap:11px;padding:0 0 16px}.history-event__dot{width:8px;height:8px;flex-shrink:0;margin-top:4px;border-radius:50%;background:var(--accent)}.history-event strong,.history-event small,.history-event code{display:block}.history-event strong{color:var(--text-bright);font-size:12px}.history-event small{margin-top:4px;color:var(--muted-light);font-size:10px}.history-event code{margin-top:4px;color:var(--muted);font-size:9px}@media(max-width:850px){.history-layout{grid-template-columns:1fr}.history-facts{grid-template-columns:repeat(2,1fr)}}
</style>
