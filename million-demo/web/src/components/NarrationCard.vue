<template>
  <div class="narration">
    <div class="meta">
      <span class="step">{{ step + 1 }} / {{ total }}</span>
      <div class="dots">
        <span v-for="i in total" :key="i" class="dot" :class="{ on: i - 1 <= step }" />
      </div>
    </div>
    <h3>{{ title }}</h3>
    <p>{{ body }}</p>
    <button class="skip" @click="$emit('skip')">跳过演示 »</button>
  </div>
</template>

<script setup>
defineProps({
  step: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  title: { type: String, default: '' },
  body: { type: String, default: '' }
})
defineEmits(['skip'])
</script>

<style scoped>
.narration {
  position: absolute; left: 50%; transform: translateX(-50%); bottom: 34px;
  width: min(560px, calc(100vw - 40px)); z-index: 20;
  background: rgba(13, 17, 23, 0.82); backdrop-filter: blur(8px);
  border: 1px solid rgba(79, 163, 199, 0.35); border-radius: 12px;
  padding: 16px 20px 14px; color: #e6edf3;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}
.meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.step { font-size: 11px; color: #4fa3c7; letter-spacing: 1px; font-weight: 700; }
.dots { display: flex; gap: 5px; }
.dot { width: 16px; height: 3px; border-radius: 2px; background: #2a3540; }
.dot.on { background: #4fa3c7; }
h3 { margin: 0 0 6px; font-size: 16px; color: #fff; }
p { margin: 0 0 10px; font-size: 12.5px; line-height: 1.7; color: #aebccb; }
.skip {
  background: transparent; border: none; color: #5a6a78; font-size: 12px;
  cursor: pointer; padding: 0; text-decoration: underline; text-underline-offset: 3px;
}
.skip:hover { color: #4fa3c7; }
</style>
