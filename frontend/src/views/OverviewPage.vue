<!-- Overview: the dashboard's recording-friendly product story. -->
<script setup>
import { computed } from 'vue'
import { boundaries } from '../model.js'

const props = defineProps({ stats: { type: Object, default: null } })
const emit = defineEmits(['navigate-live', 'navigate-history'])
const totalCalls = computed(() => props.stats
  ? (props.stats.globalNvidiaInvocationCount ?? 0) + (props.stats.localNvidiaInvocationCount ?? 0) + (props.stats.euScalewayInvocationCount ?? 0)
  : '—')

const problems = [
  { label: 'Data placement', question: 'Can this document leave the EU?', symbol: '01' },
  { label: 'Model authority', question: 'Should an LLM execute a €18,400 payment?', symbol: '02' },
  { label: 'Auditability', question: 'Can we prove what happened later?', symbol: '03' },
]
const guarantees = [
  { label: 'Data placement', source: 'CONFIDENTIAL / EU_ONLY', result: 'GLOBAL CLOUD', detail: 'Denied before provider invocation', tone: 'danger' },
  { label: 'Action governance', source: 'Model proposes €18,400', result: 'HUMAN APPROVAL', detail: 'High-risk tool remains suspended', tone: 'warning' },
  { label: 'Verifiable evidence', source: 'Every decision', result: 'CHAIN VALID', detail: 'Hash-chained runtime evidence', tone: 'success' },
]
const comparison = [
  ['Prompt says what should happen', 'Runtime enforces what may happen'],
  ['Application chooses the provider', 'Policy authorizes placement'],
  ['Model calls tools', 'Runtime authorizes effects'],
  ['Human approval is bolted on', 'Approval is execution state'],
  ['Logs describe events', 'Evidence proves decisions'],
]

function openLive(focus) { emit('navigate-live', focus) }
</script>

<template>
  <div class="overview-page">
    <section class="overview-hero">
      <div class="overview-hero__copy">
        <span class="overview-kicker">TRAMAI <i /> SOVEREIGN AGENT GOVERNANCE</span>
        <h1>AI can reason.<br /><span>It shouldn't decide its own boundaries.</span></h1>
        <p class="overview-hero__lede">TramAI is an open-source governance runtime for AI agents. It controls where sensitive data may be processed and what consequential actions models may execute.</p>
        <div class="overview-hero__actions">
          <button class="btn btn--primary" @click="openLive('placement')">Open live demo <span>↗</span></button>
          <button class="btn btn--ghost" @click="openLive('action')">Prove action governance</button>
        </div>
      </div>
      <div class="overview-hero__statement" aria-label="Models propose. TramAI decides.">
        <span>MODELS</span><strong>PROPOSE.</strong><span class="overview-hero__rule" /><span class="overview-hero__accent">TRAMAI</span><strong>DECIDES.</strong>
      </div>
      <div class="overview-tech-strip">
        <div><span>OPEN SOURCE</span><strong>Kotlin / Spring</strong></div>
        <div><span>NVIDIA</span><strong>RTX + Nemotron</strong></div>
        <div><span>JVM</span><strong>Production runtime</strong></div>
        <div class="overview-tech-strip__live"><span>LIVE CALLS</span><strong>{{ totalCalls }}</strong></div>
      </div>
    </section>

    <section class="overview-section">
      <div class="overview-section__heading"><span class="overview-index">01 / THE PROBLEM</span><h2>Today's AI applications leave the boundary to the model.</h2></div>
      <div class="overview-problem-grid">
        <article v-for="problem in problems" :key="problem.label" class="overview-problem-card"><span class="overview-card-number">{{ problem.symbol }}</span><span class="overview-card-label">{{ problem.label }}</span><strong>{{ problem.question }}</strong><span class="overview-question">?</span></article>
      </div>
      <p class="overview-punchline">Prompts are instructions. <span>They are not enforcement boundaries.</span></p>
    </section>

    <section class="overview-section overview-section--answer">
      <div class="overview-section__heading"><span class="overview-index">02 / THE ANSWER</span><h2>Three guarantees. One policy plane.</h2></div>
      <div class="overview-guarantee-grid">
        <article v-for="guarantee in guarantees" :key="guarantee.label" class="overview-guarantee-card" :class="`overview-guarantee-card--${guarantee.tone}`"><span class="overview-card-label">{{ guarantee.label }}</span><strong>{{ guarantee.source }}</strong><span class="overview-flow-arrow">↓</span><strong class="overview-guarantee-result">{{ guarantee.result }}</strong><small>{{ guarantee.detail }}</small></article>
      </div>
    </section>

    <section class="overview-section overview-architecture">
      <div class="overview-section__heading overview-section__heading--split"><div><span class="overview-index">03 / THE ARCHITECTURE</span><h2>One governance contract.<br /><span>Multiple models. Multiple trust zones.</span></h2></div><p>Placement is proposed by the application, then authorized by TramAI before inference begins.</p></div>
      <div class="overview-route-diagram">
        <div class="overview-route-node overview-route-node--app"><span>APPLICATION</span><strong>Process document</strong><small>route proposal</small></div><span class="overview-route-arrow">→</span><div class="overview-route-node overview-route-node--tramai"><span>TRAMAI</span><strong>Policy plane</strong><small>classify · authorize · audit</small></div><span class="overview-route-arrow">→</span>
        <div class="overview-route-zones">
          <article v-for="boundary in boundaries" :key="boundary.id" class="overview-zone" :class="`overview-zone--${boundary.id.toLowerCase().replace('_', '-')}`"><div class="overview-zone__top"><span>{{ boundary.title }}</span><b>✓</b></div><strong>{{ boundary.stack[0] }}</strong><span>{{ boundary.stack[2] }}</span><small>{{ boundary.id === 'LOCAL_NVIDIA' ? 'RESTRICTED / LOCAL_ONLY' : boundary.id === 'EU_CLOUD' ? 'CONFIDENTIAL / EU_ONLY' : 'PUBLIC / ANY' }}</small></article>
        </div>
      </div>
    </section>

    <section class="overview-section overview-nvidia">
      <div class="overview-section__heading"><span class="overview-index">04 / NVIDIA + TRAMAI</span><h2>NVIDIA provides the intelligence and compute.<br /><span>TramAI provides the governance boundary.</span></h2></div>
      <div class="overview-nvidia-grid"><div class="overview-nvidia-pillar"><span>GLOBAL</span><strong>NVIDIA hosted Nemotron</strong><small>Build.NVIDIA.com inference</small></div><div class="overview-nvidia-core"><span>TRAMAI</span><strong>Governs placement<br />and effects</strong></div><div class="overview-nvidia-pillar overview-nvidia-pillar--local"><span>LOCAL</span><strong>NVIDIA RTX / Qwen</strong><small>llama.cpp local action model</small></div></div>
    </section>

    <section class="overview-section overview-difference">
      <div class="overview-section__heading"><span class="overview-index">05 / THE DIFFERENCE</span><h2>Governance survives model changes.</h2></div>
      <div class="overview-comparison"><div class="overview-comparison__head"><span>TYPICAL AI APPLICATION</span><span>TRAMAI</span></div><div v-for="row in comparison" :key="row[0]" class="overview-comparison__row"><span>{{ row[0] }}</span><b>→</b><strong>{{ row[1] }}</strong></div></div>
    </section>

    <section class="overview-cta"><span class="overview-index">LIVE PROOF</span><h2>Don't trust the diagram.<br /><span>Test the boundary.</span></h2><p>Use the real PDF flow to watch TramAI deny a forbidden route, govern a high-risk action, and verify the audit chain.</p><div class="overview-cta__actions"><button class="btn btn--primary" @click="openLive('placement')">Prove data placement <span>↗</span></button><button class="btn btn--ghost" @click="openLive('action')">Prove action governance <span>↗</span></button><button class="text-btn" @click="emit('navigate-history')">View processed documents</button></div><div class="overview-cta__signature">THE MODEL REASONS. <span>TRAMAI GOVERNS.</span></div></section>
  </div>
</template>

<style scoped>
.overview-page{max-width:1320px;margin:0 auto}.overview-hero{min-height:440px;padding:38px 42px 26px;border:1px solid var(--line-mid);border-radius:var(--r-lg);background:radial-gradient(circle at 90% 10%,rgba(118,185,0,.12),transparent 34%),linear-gradient(135deg,rgba(20,31,17,.98),rgba(9,13,9,.97) 68%);display:grid;grid-template-columns:minmax(0,1.15fr) .85fr;align-items:center;gap:28px;overflow:hidden;position:relative}.overview-kicker,.overview-index,.overview-card-label{color:var(--accent-bright);font:800 9px var(--font-mono);letter-spacing:.16em;text-transform:uppercase}.overview-kicker{display:flex;align-items:center;gap:9px}.overview-kicker i{width:22px;height:1px;background:var(--accent)}.overview-hero h1{max-width:760px;margin:20px 0 16px;color:var(--text-bright);font-size:clamp(2.5rem,5vw,5rem);line-height:.98;letter-spacing:-.065em}.overview-hero h1 span,.overview-section h2 span{color:var(--accent-bright)}.overview-hero__lede{max-width:620px;color:var(--muted-light);font-size:15px;line-height:1.65}.overview-hero__actions,.overview-cta__actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:26px}.overview-hero__actions .btn span,.overview-cta .btn span{font-size:16px}.overview-hero__statement{justify-self:end;display:flex;flex-direction:column;align-items:flex-end;color:var(--muted);font:700 clamp(1.5rem,3vw,3rem) var(--font-mono);line-height:.95;letter-spacing:-.08em;opacity:.78}.overview-hero__statement strong{color:var(--text-bright)}.overview-hero__statement .overview-hero__accent{margin-top:18px;color:var(--accent-bright)}.overview-hero__rule{width:116px;height:1px;margin:13px 0 0;background:var(--accent);box-shadow:0 0 18px var(--accent)}.overview-tech-strip{grid-column:1/-1;display:grid;grid-template-columns:repeat(4,1fr);border-top:1px solid var(--line);padding-top:18px;gap:18px}.overview-tech-strip div{display:flex;flex-direction:column;gap:5px}.overview-tech-strip span{color:var(--muted);font:700 9px var(--font-mono);letter-spacing:.12em}.overview-tech-strip strong{color:var(--text-bright);font-size:12px}.overview-tech-strip__live{justify-self:end;text-align:right}.overview-tech-strip__live strong{color:var(--accent-bright);font:700 16px var(--font-mono)}.overview-section{padding:86px 16px 0}.overview-section__heading{max-width:720px;margin-bottom:28px}.overview-section__heading h2,.overview-cta h2{margin-top:10px;color:var(--text-bright);font-size:clamp(1.8rem,3.2vw,3rem);line-height:1.03;letter-spacing:-.05em}.overview-section__heading--split{max-width:none;display:flex;justify-content:space-between;gap:30px;align-items:end}.overview-section__heading--split p{max-width:330px;color:var(--muted);font-size:12px;line-height:1.6}.overview-problem-grid,.overview-guarantee-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.overview-problem-card,.overview-guarantee-card{min-height:230px;padding:21px;border:1px solid var(--line);border-radius:var(--r-md);background:rgba(255,255,255,.018);position:relative;display:flex;flex-direction:column}.overview-problem-card:hover,.overview-guarantee-card:hover{border-color:var(--line-mid)}.overview-card-number{color:var(--muted);font:700 11px var(--font-mono)}.overview-problem-card .overview-card-label{margin-top:34px}.overview-problem-card strong{max-width:260px;margin-top:15px;color:var(--text-bright);font-size:20px;line-height:1.1;letter-spacing:-.03em}.overview-question{margin-top:auto;color:rgba(118,185,0,.22);font:900 76px var(--font-mono);line-height:.55;align-self:end}.overview-punchline{margin:25px 0 0;color:var(--muted-light);font-size:15px}.overview-punchline span{color:var(--danger)}.overview-section--answer{padding-top:96px}.overview-guarantee-card{min-height:245px;background:linear-gradient(160deg,rgba(16,24,16,.94),rgba(9,13,9,.92))}.overview-guarantee-card strong{margin-top:27px;color:var(--text-bright);font:700 15px var(--font-mono)}.overview-flow-arrow{margin:17px 0 4px;color:var(--muted);font-size:20px}.overview-guarantee-result{margin-top:0!important;color:var(--accent-bright)!important}.overview-guarantee-card small{margin-top:auto;color:var(--muted);font-size:11px}.overview-guarantee-card--danger{border-color:var(--danger-border)}.overview-guarantee-card--danger .overview-guarantee-result{color:#ff9292!important}.overview-guarantee-card--warning{border-color:rgba(245,166,35,.28)}.overview-guarantee-card--warning .overview-guarantee-result{color:#f1c77b!important}.overview-architecture{padding-top:100px}.overview-route-diagram{display:grid;grid-template-columns:1fr 34px 1.15fr 34px 2.3fr;align-items:center;gap:10px}.overview-route-node{min-height:132px;padding:20px;border:1px solid var(--line);border-radius:var(--r-md);background:var(--panel);display:flex;flex-direction:column;justify-content:center}.overview-route-node span,.overview-zone__top span,.overview-nvidia-pillar span{color:var(--accent-bright);font:800 9px var(--font-mono);letter-spacing:.15em}.overview-route-node strong{margin-top:13px;color:var(--text-bright);font-size:16px}.overview-route-node small{margin-top:6px;color:var(--muted);font-size:10px}.overview-route-node--tramai{border-color:var(--line-strong);background:linear-gradient(145deg,rgba(118,185,0,.12),rgba(12,18,11,.95))}.overview-route-arrow{color:var(--accent);font-size:28px;text-align:center}.overview-route-zones{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.overview-zone{min-height:164px;padding:14px;border:1px solid var(--line);border-radius:10px;background:rgba(255,255,255,.018);display:flex;flex-direction:column}.overview-zone__top{display:flex;justify-content:space-between;align-items:center}.overview-zone__top b{color:var(--accent);font-size:16px}.overview-zone>strong{margin-top:20px;color:var(--text-bright);font-size:12px}.overview-zone>span{margin-top:5px;color:var(--muted-light);font-size:11px}.overview-zone>small{margin-top:auto;color:var(--muted);font:9px var(--font-mono)}.overview-zone--eu-cloud{border-color:rgba(130,201,130,.28)}.overview-zone--global-cloud{border-color:rgba(118,185,0,.34)}.overview-nvidia{padding-top:100px}.overview-nvidia-grid{display:grid;grid-template-columns:1fr 1fr 1fr;align-items:stretch;gap:12px}.overview-nvidia-pillar,.overview-nvidia-core{min-height:150px;padding:22px;border:1px solid var(--line);border-radius:var(--r-md);display:flex;flex-direction:column;justify-content:center}.overview-nvidia-pillar strong{margin-top:16px;color:var(--text-bright);font-size:15px}.overview-nvidia-pillar small{margin-top:7px;color:var(--muted);font-size:11px}.overview-nvidia-pillar--local{border-color:var(--line-strong)}.overview-nvidia-core{align-items:center;text-align:center;border-color:var(--accent);background:var(--accent-dim)}.overview-nvidia-core span{color:var(--accent-bright);font:800 10px var(--font-mono);letter-spacing:.16em}.overview-nvidia-core strong{margin-top:14px;color:var(--text-bright);font-size:18px;line-height:1.1}.overview-difference{padding-top:100px}.overview-comparison{border:1px solid var(--line);border-radius:var(--r-md);overflow:hidden}.overview-comparison__head,.overview-comparison__row{display:grid;grid-template-columns:1fr 30px 1fr;gap:15px;align-items:center;padding:15px 20px}.overview-comparison__head{background:rgba(118,185,0,.08);color:var(--muted);font:800 9px var(--font-mono);letter-spacing:.12em}.overview-comparison__head span:last-child{color:var(--accent-bright)}.overview-comparison__row{border-top:1px solid var(--line);color:var(--muted-light);font-size:12px}.overview-comparison__row b{color:var(--accent);text-align:center}.overview-comparison__row strong{color:var(--text-bright);font-weight:650}.overview-cta{margin:100px 0 25px;padding:60px 42px 44px;border:1px solid var(--line-strong);border-radius:var(--r-lg);text-align:center;background:radial-gradient(circle at 50% 0,rgba(118,185,0,.15),transparent 45%),var(--panel)}.overview-cta h2{margin-top:13px}.overview-cta p{max-width:530px;margin:18px auto 0;color:var(--muted);font-size:12px;line-height:1.6}.overview-cta__actions{justify-content:center}.overview-cta__actions .text-btn{width:100%}.overview-cta__signature{margin-top:48px;color:var(--muted);font:700 12px var(--font-mono);letter-spacing:.16em}.overview-cta__signature span{color:var(--accent-bright)}
@media (max-width:950px){.overview-hero{grid-template-columns:1fr}.overview-hero__statement{justify-self:start;align-items:flex-start}.overview-route-diagram{grid-template-columns:1fr}.overview-route-arrow{transform:rotate(90deg)}.overview-route-zones{grid-template-columns:repeat(3,1fr)}}
@media (max-width:680px){.overview-hero{padding:28px 22px 22px}.overview-hero h1{font-size:2.7rem}.overview-tech-strip{grid-template-columns:repeat(2,1fr)}.overview-tech-strip__live{justify-self:start;text-align:left}.overview-problem-grid,.overview-guarantee-grid,.overview-nvidia-grid,.overview-route-zones{grid-template-columns:1fr}.overview-section{padding-top:62px}.overview-section__heading--split{display:block}.overview-section__heading--split p{margin-top:16px}.overview-comparison__head,.overview-comparison__row{grid-template-columns:1fr 20px 1fr;gap:8px;padding:13px 10px;font-size:10px}.overview-cta{padding:38px 20px 30px;margin-top:70px}.overview-cta__actions .btn{width:100%}}
</style>
