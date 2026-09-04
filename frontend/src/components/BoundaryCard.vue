<script setup>
defineProps({
  boundary: { type: Object, required: true },
  allowed:  { type: Boolean, required: true },
  selected: { type: Boolean, required: true },
  delta:    { type: Number, default: null },
})
</script>

<template>
  <article
    class="boundary-card"
    :class="{
      'boundary-card--selected': selected,
      'boundary-card--denied':   !allowed && !selected,
    }"
  >
    <div class="boundary-card__header">
      <span class="boundary-card__title">{{ boundary.title }}</span>
      <span v-if="selected"       class="pill pill--selected">SELECTED</span>
      <span v-else-if="allowed"  class="pill pill--allowed">ALLOWED</span>
      <span v-else               class="pill pill--denied">DENIED</span>
    </div>

    <p class="boundary-card__sub">{{ boundary.subtitle }}</p>

    <ul class="boundary-stack">
      <li v-for="item in boundary.stack" :key="item">{{ item }}</li>
    </ul>

    <div class="boundary-card__footer">
      <span>Provider calls Δ</span>
      <strong :class="{ 'text-accent': delta !== null && delta > 0 }">
        {{ delta === null ? '—' : delta > 0 ? `+${delta}` : delta }}
      </strong>
    </div>
  </article>
</template>
