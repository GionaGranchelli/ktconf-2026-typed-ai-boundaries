<script setup>
defineProps({
  boundary: { type: Object, required: true },
  allowed: { type: Boolean, required: true },
  selected: { type: Boolean, required: true },
  delta: { type: Number, default: null },
})
</script>

<template>
  <article class="boundary-card" :class="{ selected, denied: !allowed }">
    <div class="boundary-card__topline">
      <span class="eyebrow">{{ boundary.title }}</span>
      <span v-if="selected" class="pill pill--selected">SELECTED</span>
      <span v-else-if="allowed" class="pill pill--allowed">ALLOWED</span>
      <span v-else class="pill pill--denied">DENIED</span>
    </div>
    <p class="boundary-card__subtitle">{{ boundary.subtitle }}</p>
    <ul class="boundary-stack">
      <li v-for="item in boundary.stack" :key="item">{{ item }}</li>
    </ul>
    <div class="boundary-card__footer">
      <span>Provider call delta</span>
      <strong>{{ delta === null ? '—' : delta }}</strong>
    </div>
  </article>
</template>
