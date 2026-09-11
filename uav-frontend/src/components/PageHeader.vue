<template>
  <div class="page-header">
    <div>
      <h2 class="page-title">{{ title }}</h2>
      <div v-if="subtitle" class="page-subtitle">{{ subtitle }}</div>
    </div>
    <div v-if="$slots.actions" class="header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>
