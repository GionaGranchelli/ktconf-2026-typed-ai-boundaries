<script setup>
/**
 * App.vue — TramAI Governance Console shell
 *
 * Responsibilities:
 *  - persistent sidebar navigation (no router dependency)
 *  - backend health / global stats polling
 *  - shared reactive state passed as props into each view
 *
 * No policy logic lives here. All proof values come from backend responses.
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getStats, health } from './api.js'
import OverviewPage      from './views/OverviewPage.vue'
import DocumentFlowPage  from './views/DocumentFlowPage.vue'
import PolicyMatrixPage  from './views/PolicyMatrixPage.vue'
import EvidencePage      from './views/EvidencePage.vue'

// ── Navigation ─────────────────────────────────────────────────
const views = [
  { id: 'live-governance', label: 'Live Governance', icon: 'play' },
  { id: 'policy-matrix',   label: 'Policy Matrix',   icon: 'shield' },
  { id: 'evidence',        label: 'Evidence',         icon: 'chain' },
  { id: 'overview',        label: 'Overview',          icon: 'grid' },
]

const activeView = ref('live-governance')
const pageTitle  = computed(() => views.find(v => v.id === activeView.value)?.label ?? '')

// ── Backend health ──────────────────────────────────────────────
const backendStatus = ref('checking')   // 'checking' | 'online' | 'offline'

// ── Global stats (polled) ───────────────────────────────────────
const globalStats   = ref(null)
const sessionBefore = ref(null)   // snapshot at page-load for deltas

let pollInterval = null

async function refreshStats() {
  try {
    const s = await getStats()
    globalStats.value = s
    if (!sessionBefore.value) sessionBefore.value = s
    if (backendStatus.value !== 'online') backendStatus.value = 'online'
  } catch {
    backendStatus.value = 'offline'
  }
}

onMounted(async () => {
  try {
    await health()
    backendStatus.value = 'online'
  } catch {
    backendStatus.value = 'offline'
  }
  await refreshStats()
  // Soft-poll every 15 s to keep counters fresh without spamming
  pollInterval = setInterval(refreshStats, 15_000)
})

onUnmounted(() => clearInterval(pollInterval))

// DocumentFlow emits stats-updated after every action
function onStatsUpdated(stats) {
  globalStats.value = stats
}
</script>

<template>
  <div class="app-shell">
    <!-- ─── Sidebar ──────────────────────────────────────────── -->
    <aside class="sidebar">
      <div class="sidebar__logo">
        <div class="sidebar__logo-mark">T</div>
        <div>
          <div class="sidebar__logo-name">TramAI</div>
          <span class="sidebar__logo-sub">GOVERNANCE CONSOLE</span>
        </div>
      </div>

      <div class="sidebar__section-label">Navigation</div>

      <button
        v-for="v in views"
        :key="v.id"
        class="nav-item"
        :class="{ active: activeView === v.id }"
        @click="activeView = v.id"
      >
        <!-- Inline SVG icons — no icon library needed -->
        <svg class="nav-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
          <!-- play / live governance -->
          <template v-if="v.icon === 'play'">
            <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.5"/>
            <path d="M6.5 5.5l5 2.5-5 2.5V5.5z" fill="currentColor" stroke="none"/>
          </template>
          <!-- grid -->
          <template v-else-if="v.icon === 'grid'">
            <rect x="1.5" y="1.5" width="5.5" height="5.5" rx="1"/>
            <rect x="9"   y="1.5" width="5.5" height="5.5" rx="1"/>
            <rect x="1.5" y="9"   width="5.5" height="5.5" rx="1"/>
            <rect x="9"   y="9"   width="5.5" height="5.5" rx="1"/>
          </template>
          <!-- file -->
          <template v-else-if="v.icon === 'file'">
            <path d="M3 2h7l3 3v9a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z"/>
            <path d="M10 2v3h3M5 8h6M5 11h4"/>
          </template>
          <!-- shield -->
          <template v-else-if="v.icon === 'shield'">
            <path d="M8 1.5 2 4v5c0 3.3 2.7 5.4 6 6 3.3-.6 6-2.7 6-6V4L8 1.5z"/>
            <path d="m5.5 8 2 2 3.5-3.5"/>
          </template>
          <!-- chain / evidence -->
          <template v-else>
            <rect x="1.5" y="5"  width="4" height="6" rx="1.5"/>
            <rect x="10.5" y="5" width="4" height="6" rx="1.5"/>
            <path d="M5.5 8h5"/>
            <path d="M8 3.5v-2M8 14.5v-2"/>
          </template>
        </svg>
        {{ v.label }}
      </button>

      <div class="sidebar__footer">
        <strong>NVIDIA GTC Berlin</strong><br />
        The Model Is Not the Authority<br />
        <span style="font-size:10px;opacity:0.6;">One policy plane · three boundaries</span>
      </div>
    </aside>

    <!-- ─── Main ─────────────────────────────────────────────── -->
    <div class="main-area">
      <!-- Topbar -->
      <header class="topbar">
        <div class="topbar__crumb">
          TramAI · <strong>{{ pageTitle }}</strong>
        </div>
        <div class="topbar__right">
          <!-- Live total NVIDIA calls pill -->
          <span v-if="globalStats" style="font-size:11.5px;color:var(--muted);">
            NVIDIA calls:
            <strong style="color:var(--accent-bright);font-family:var(--font-mono)">
              {{
                (globalStats.globalNvidiaInvocationCount ?? 0)
                + (globalStats.localNvidiaInvocationCount  ?? 0)
                + (globalStats.euScalewayInvocationCount   ?? 0)
              }}
            </strong>
          </span>

          <!-- Backend status badge -->
          <div class="backend-badge" :class="`backend-badge--${backendStatus}`">
            <span class="status-dot" />
            Backend {{ backendStatus }}
          </div>
        </div>
      </header>

      <!-- Page content -->
      <main class="page-content">
        <DocumentFlowPage
          v-if="activeView === 'live-governance'"
          @stats-updated="onStatsUpdated"
        />

        <OverviewPage
          v-else-if="activeView === 'overview'"
          :stats="globalStats"
          :session-before="sessionBefore"
        />

        <DocumentFlowPage
          v-else-if="activeView === 'document-flow'"
          @stats-updated="onStatsUpdated"
        />

        <PolicyMatrixPage
          v-else-if="activeView === 'policy-matrix'"
        />

        <EvidencePage
          v-else-if="activeView === 'evidence'"
          :stats="globalStats"
        />
      </main>
    </div>
  </div>
</template>
