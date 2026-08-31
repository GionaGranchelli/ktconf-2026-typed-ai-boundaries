<script setup>
defineProps({
  label:  { type: String,           required: true },
  value:  { type: [Number, String], default: '—' },
  delta:  { type: Number,           default: null },
  accent: { type: Boolean,          default: false },
  danger: { type: Boolean,          default: false },
  mono:   { type: Boolean,          default: false },
})
</script>

<template>
  <div class="panel kpi-card">
    <span class="kpi-label">{{ label }}</span>
    <span
      class="kpi-value"
      :class="{
        'kpi-value--accent': accent,
        'kpi-value--danger': danger,
        'font-mono':         mono,
      }"
    >{{ value }}</span>
    <span
      v-if="delta !== null"
      class="kpi-delta"
      :class="{
        'kpi-delta--pos': delta > 0,
        'kpi-delta--neg': delta < 0,
      }"
    >
      {{ delta > 0 ? `+${delta}` : delta }} this session
    </span>
  </div>
</template>
