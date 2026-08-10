<template>
  <div class="layer-manager">
    <div class="layer-header">
      <span class="layer-icon">📋</span>
      <span>图层管理</span>
    </div>
    <div class="layer-list">
      <div
        v-for="layer in layers"
        :key="layer.key"
        class="layer-item"
        :class="{ 'layer-active': layer.visible }"
        @click="toggleLayer(layer)"
      >
        <div class="layer-toggle">
          <div class="toggle-track" :class="{ 'toggle-on': layer.visible }">
            <div class="toggle-thumb"></div>
          </div>
        </div>
        <span class="layer-icon-small">{{ layer.icon }}</span>
        <span class="layer-label">{{ layer.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  layers: { type: Array, required: true }
})

const emit = defineEmits(['toggle'])

function toggleLayer(layer) {
  layer.visible = !layer.visible
  emit('toggle', layer.key, layer.visible)
}
</script>

<style scoped>
.layer-manager {
  position: absolute;
  top: 80px;
  right: 20px;
  background: rgba(10, 20, 40, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 200, 255, 0.25);
  border-radius: 12px;
  padding: 14px 16px;
  min-width: 160px;
  z-index: 100;
  user-select: none;
}

.layer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8ab4f8;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.layer-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.layer-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  color: #8899aa;
}

.layer-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.layer-active {
  color: #e0f0ff;
}

.toggle-track {
  width: 32px;
  height: 18px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  position: relative;
  transition: all 0.3s;
}

.toggle-track.toggle-on {
  background: rgba(0, 200, 255, 0.6);
}

.toggle-thumb {
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: all 0.3s;
}

.toggle-on .toggle-thumb {
  left: 16px;
}

.layer-icon-small {
  font-size: 16px;
}

.layer-label {
  font-size: 13px;
}
</style>
