<!--
  PolicyMatrixPage — Reference visualization of the TramAI routing matrix.
  Mirrors docs/gtc/ARCHITECTURE.md. Backend is authoritative.
  No placement decision is made in the UI.
-->
<script setup>
import { boundaries, policyMatrix } from '../model.js'
</script>

<template>
  <div>
    <!-- Routing matrix table -->
    <div class="panel">
      <div class="panel-heading">
        <div>
          <span class="eyebrow">Policy reference</span>
          <div class="panel-title">TramAI routing matrix</div>
          <div class="panel-subtitle">
            Classification × Residency → authorized execution boundaries.<br/>
            The model proposes. TramAI decides. The UI only visualizes.
          </div>
        </div>
      </div>

      <div class="info-note" style="margin-bottom:18px;">
        This table mirrors <code style="font-family:var(--font-mono);font-size:10.5px">docs/gtc/ARCHITECTURE.md</code>.
        The backend TramAI policy plane is the authoritative source. No placement decision is made here.
      </div>

      <table class="policy-table">
        <thead>
          <tr>
            <th>Classification</th>
            <th>Residency</th>
            <th v-for="b in boundaries" :key="b.id">{{ b.title }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in policyMatrix" :key="`${row.classification}-${row.residency}`">
            <td>
              <span
                class="pill"
                :class="{
                  'pill--denied':  row.classification === 'RESTRICTED',
                  'pill--medium':  row.classification === 'CONFIDENTIAL',
                  'pill--allowed': row.classification === 'PUBLIC',
                }"
              >{{ row.classification }}</span>
            </td>
            <td>
              <code style="font-family:var(--font-mono);font-size:11px;color:var(--muted-light)">
                {{ row.residency }}
              </code>
            </td>
            <td v-for="b in boundaries" :key="b.id">
              <span v-if="row[b.id]"  class="policy-allow">✓ ALLOW</span>
              <span v-else            class="policy-deny">✕ DENY</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Boundary detail cards -->
    <div class="grid-3 gap-top">
      <div v-for="b in boundaries" :key="b.id" class="panel">
        <span class="eyebrow">{{ b.title }}</span>
        <div class="panel-title" style="margin:5px 0 4px;">{{ b.subtitle }}</div>
        <p class="panel-subtitle" style="margin-bottom:12px;">{{ b.description }}</p>
        <hr class="divider" />
        <ul class="boundary-stack">
          <li v-for="item in b.stack" :key="item">{{ item }}</li>
        </ul>
      </div>
    </div>

    <!-- Tool policy -->
    <div class="panel gap-top">
      <span class="eyebrow">Tool policy</span>
      <div class="panel-title" style="margin:6px 0 8px;">HIGH-risk tool suspension proof</div>
      <p class="microcopy" style="max-width:740px;">
        The model may propose <span class="authority-tool">schedule-payment</span>.
        TramAI intercepts calls annotated <strong>HIGH / HUMAN_REQUIRED</strong> and suspends
        the workflow before any side effect executes. The payment counter proves:
        <em>0 at suspension → exactly 1 after human approval → rejected on duplicate → 0 on denial</em>.
        Audit evidence provides hash-chained records of every state transition.
      </p>
      <div class="metric-grid" style="margin-top:14px; max-width:520px;">
        <div class="metric-cell">
          <span class="metric-label">Risk level</span>
          <span class="metric-value" style="font-size:1.1rem;">HIGH</span>
        </div>
        <div class="metric-cell">
          <span class="metric-label">Policy</span>
          <span class="metric-value" style="font-size:1.1rem;">HUMAN_REQUIRED</span>
        </div>
      </div>
    </div>
  </div>
</template>
