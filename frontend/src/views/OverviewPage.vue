<!--
  OverviewPage — Live governance counters + three-boundary status.
  All values sourced from GET /governance/stats — no fabricated numbers.
-->
<script setup>
import { computed } from 'vue'
import BoundaryCard from '../components/BoundaryCard.vue'
import KpiCard      from '../components/KpiCard.vue'
import { boundaries, counterDelta } from '../model.js'

const props = defineProps({
  stats:         { type: Object, default: null },
  sessionBefore: { type: Object, default: null },
})

const totalNvidia = computed(() => {
  if (!props.stats) return '—'
  return (
    (props.stats.globalNvidiaInvocationCount ?? 0) +
    (props.stats.localNvidiaInvocationCount  ?? 0) +
    (props.stats.euScalewayInvocationCount   ?? 0)
  )
})

const nvidiaSessionDelta = computed(() => {
  if (!props.sessionBefore || !props.stats) return null
  const before = (props.sessionBefore.globalNvidiaInvocationCount ?? 0)
               + (props.sessionBefore.localNvidiaInvocationCount  ?? 0)
               + (props.sessionBefore.euScalewayInvocationCount   ?? 0)
  const after  = (props.stats.globalNvidiaInvocationCount ?? 0)
               + (props.stats.localNvidiaInvocationCount  ?? 0)
               + (props.stats.euScalewayInvocationCount   ?? 0)
  return after - before
})

// Boundary cards on the overview have no selected/allowed state yet — show raw counters
const allowedAll = new Set(['LOCAL_NVIDIA', 'EU_CLOUD', 'GLOBAL_CLOUD'])
</script>

<template>
  <div>
    <!-- KPI row -->
    <span class="eyebrow-label">Live governance counters</span>
    <div class="grid-4">
      <KpiCard
        label="NVIDIA Invocations"
        :value="totalNvidia"
        accent
        :delta="nvidiaSessionDelta"
      />
      <KpiCard
        label="Payments Executed"
        :value="stats?.paymentExecutionCount ?? '—'"
        :delta="counterDelta(sessionBefore, stats, 'paymentExecutionCount')"
        :danger="(stats?.paymentExecutionCount ?? 0) > 0"
      />
      <KpiCard
        label="Email Notifications"
        :value="stats?.emailNotificationCount ?? '—'"
        :delta="counterDelta(sessionBefore, stats, 'emailNotificationCount')"
      />
      <KpiCard
        label="Legacy (Deterministic)"
        :value="stats ? (stats.cloudInvocationCount ?? 0) + (stats.localInvocationCount ?? 0) : '—'"
        mono
      />
    </div>

    <!-- Boundary grid -->
    <span class="eyebrow-label gap-top">Execution boundaries</span>
    <div class="grid-3">
      <BoundaryCard
        v-for="b in boundaries"
        :key="b.id"
        :boundary="b"
        :allowed="allowedAll.has(b.id)"
        :selected="false"
        :delta="stats ? (stats[b.statsKey] ?? 0) : null"
      />
    </div>

    <div v-if="!stats" class="panel gap-top">
      <div class="empty-state">
        Backend unreachable — governance counters unavailable.<br/>
        Use <strong>Document Flow</strong> to process a PDF once the backend is running.
      </div>
    </div>

    <div v-else class="info-note gap-top">
      Counters are cumulative since server start. Deltas shown above are scoped to this browser session.
      All numbers are sourced exclusively from <code style="font-family:var(--font-mono);font-size:10.5px">GET /governance/stats</code>.
    </div>
  </div>
</template>

<style scoped>
.gap-top { margin-top: 22px; }
</style>
