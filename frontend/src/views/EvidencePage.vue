<!--
  EvidencePage — Running view of backend invocation counters and
  the governance proof chain status.

  This page deliberately reads counters directly from the global stats
  prop (polled by App.vue). Audit chain evidence is only fetchable after
  an approval/denial and is shown in Document Flow instead.
-->
<script setup>
import { computed } from 'vue'
import { boundaries } from '../model.js'

const props = defineProps({
  stats: { type: Object, default: null },
})

const totalNvidia = computed(() => {
  if (!props.stats) return null
  return (
    (props.stats.globalNvidiaInvocationCount ?? 0) +
    (props.stats.localNvidiaInvocationCount  ?? 0) +
    (props.stats.euScalewayInvocationCount   ?? 0)
  )
})

const rows = computed(() => {
  if (!props.stats) return []
  return [
    ...boundaries.map(b => ({
      label:   `${b.title} (${b.subtitle})`,
      key:     b.statsKey,
      value:   props.stats[b.statsKey] ?? 0,
      nvidia:  true,
    })),
    { label: 'Legacy Cloud (deterministic)',  key: 'cloudInvocationCount',  value: props.stats.cloudInvocationCount  ?? 0, nvidia: false },
    { label: 'Legacy Local (deterministic)',  key: 'localInvocationCount',  value: props.stats.localInvocationCount  ?? 0, nvidia: false },
    { label: 'Payments Executed',             key: 'paymentExecutionCount', value: props.stats.paymentExecutionCount ?? 0, nvidia: false },
    { label: 'Email Notifications',           key: 'emailNotificationCount',value: props.stats.emailNotificationCount?? 0, nvidia: false },
  ]
})
</script>

<template>
  <div>
    <span class="eyebrow-label">Backend proof counters</span>

    <!-- No backend -->
    <div v-if="!stats" class="panel">
      <div class="empty-state">
        Backend unreachable — counters unavailable.<br/>
        Start the Spring Boot application and reload.
      </div>
    </div>

    <template v-else>
      <!-- Summary row -->
      <div class="grid-3">
        <div class="panel kpi-card">
          <span class="kpi-label">Total NVIDIA invocations</span>
          <span class="kpi-value kpi-value--accent">{{ totalNvidia }}</span>
          <span class="kpi-delta">across all three execution boundaries</span>
        </div>
        <div class="panel kpi-card">
          <span class="kpi-label">Payments Executed</span>
          <span
            class="kpi-value"
            :class="{ 'kpi-value--danger': (stats.paymentExecutionCount ?? 0) > 0 }"
          >{{ stats.paymentExecutionCount ?? '—' }}</span>
          <span class="kpi-delta">exactly-once ledger</span>
        </div>
        <div class="panel kpi-card">
          <span class="kpi-label">Email Notifications</span>
          <span class="kpi-value">{{ stats.emailNotificationCount ?? '—' }}</span>
        </div>
      </div>

      <!-- Detailed counter table -->
      <div class="panel gap-top">
        <span class="eyebrow">Counter breakdown</span>
        <div class="panel-subtitle" style="margin-bottom:14px;">
          All counters come from <code style="font-family:var(--font-mono);font-size:10.5px">GET /governance/stats</code>.
          Counters are cumulative since server start and never reset.
        </div>

        <table class="policy-table">
          <thead>
            <tr>
              <th>Provider / Metric</th>
              <th>Invocations</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.key">
              <td>{{ row.label }}</td>
              <td>
                <strong style="font-family:var(--font-mono);font-size:14px;">
                  {{ row.value }}
                </strong>
              </td>
              <td>
                <span v-if="row.nvidia" class="pill pill--allowed">NVIDIA governed</span>
                <span v-else            class="pill pill--neutral">Deterministic</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Proof note -->
      <div class="info-note gap-top">
        <div>
          The provider call delta between a <em>before</em> and <em>after</em> snapshot proves
          that a denied boundary was never invoked (delta = 0). The payment counter proves
          exactly-once execution: it must be 0 at suspension, 1 after approval, and rejected
          on a duplicate attempt. Use <strong>Live Governance</strong> to generate a live proof run
          and inspect the full hash-chained audit timeline.
        </div>
      </div>
    </template>
  </div>
</template>


<style scoped>
.gap-top { margin-top: 22px; }
</style>
