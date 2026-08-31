<script setup>
const props = defineProps({
  events:     { type: Array,   default: () => [] },
  chainValid: { type: Boolean, default: null },
})

function shortName(ev) {
  return ev?.eventType || ev?.type || ev?.name || '(event)'
}
function eventHash(ev) {
  return ev?.hash || ev?.eventHash || null
}
function eventTs(ev) {
  if (!ev?.timestamp) return null
  try { return new Date(ev.timestamp).toLocaleTimeString() } catch { return null }
}
</script>

<template>
  <div class="audit-timeline">
    <div class="audit-timeline__header">
      <span class="eyebrow-label" style="margin-bottom:0">Audit chain</span>
      <span class="pill" :class="chainValid === true ? 'pill--allowed' : chainValid === false ? 'pill--denied' : 'pill--neutral'">
        {{ chainValid === true ? 'CHAIN VALID' : chainValid === false ? 'CHAIN INVALID' : 'PENDING' }}
      </span>
    </div>

    <div class="audit-timeline__list">
      <div v-for="(ev, i) in events" :key="i" class="audit-event">
        <div class="audit-event__spine">
          <div class="audit-event__dot" />
          <div v-if="i < events.length - 1" class="audit-event__line" />
        </div>
        <div class="audit-event__body">
          <div class="audit-event__row">
            <span class="audit-event__name">{{ shortName(ev) }}</span>
            <span v-if="eventTs(ev)" class="audit-event__ts font-mono">{{ eventTs(ev) }}</span>
          </div>
          <div v-if="eventHash(ev)" class="audit-event__hash font-mono">{{ String(eventHash(ev)).slice(0, 20) }}…</div>
        </div>
      </div>
      <div v-if="!events.length" class="empty-state" style="min-height:50px;font-size:11px;">
        Audit events appear after approve or deny.
      </div>
    </div>
  </div>
</template>

<style scoped>
.audit-timeline { display: flex; flex-direction: column; }
.audit-timeline__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.audit-timeline__list { display: flex; flex-direction: column; }
.audit-event { display: flex; gap: 10px; }
.audit-event__spine { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; width: 14px; }
.audit-event__dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); flex-shrink: 0; margin-top: 7px; }
.audit-event__line { flex: 1; width: 1px; background: var(--line-mid); margin: 2px 0; min-height: 8px; }
.audit-event__body { flex: 1; padding-bottom: 9px; min-width: 0; }
.audit-event__row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.audit-event__name { font-size: 11.5px; font-weight: 600; color: var(--text-bright); }
.audit-event__ts { font-size: 9.5px; color: var(--muted); }
.audit-event__hash { font-size: 9px; color: var(--muted); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
