<template>
  <el-dialog :model-value="modelValue" title="告警抑制规则" width="560px" @update:model-value="emit('update:modelValue', $event)" @open="emit('open')">
    <div class="suppress-group">
      <div class="suppress-group-title">按告警类型（勾选即生效）</div>
      <el-checkbox-group :model-value="checkedTypes" @update:model-value="setTypes">
        <el-checkbox-button v-for="d in alarmTypeItems" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox-button>
      </el-checkbox-group>
    </div>
    <div class="suppress-group">
      <div class="suppress-group-title">按级别</div>
      <el-checkbox-group :model-value="checkedLevels" @update:model-value="setLevels">
        <el-checkbox-button v-for="d in alarmLevelItems" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox-button>
      </el-checkbox-group>
    </div>
    <div class="suppress-group">
      <div class="suppress-group-title">按无人机 SN</div>
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <el-input v-model="newSn" size="small" placeholder="输入SN后回车添加" @keyup.enter="addSn" />
        <el-button size="small" @click="addSn">添加</el-button>
      </div>
      <div class="sn-tags">
        <el-tag v-for="r in snRules" :key="r.id" closable size="small" style="margin:0 6px 6px 0" @close="emit('remove', { droneSn: r.droneSn })">{{ r.droneSn }}</el-tag>
        <span v-if="!snRules.length" style="color:var(--el-text-color-secondary);font-size:12px">未指定</span>
      </div>
    </div>
    <div style="color:var(--el-text-color-secondary);font-size:12px">
      规则由服务端记录并在告警生成侧过滤：命中的告警不再推送、不再落库。
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useDict } from '@/composables/useDict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 服务端规则列表 [{ id, alarmType, alarmLevel, droneSn }] */
  rules: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'add', 'remove', 'open'])

const { items: alarmTypeItems } = useDict('alarm_type')
const { items: alarmLevelItems } = useDict('alarm_level')

const newSn = ref('')

const checkedTypes = computed(() => [...new Set(props.rules.filter(r => r.alarmType).map(r => r.alarmType))])
const checkedLevels = computed(() => [...new Set(props.rules.filter(r => r.alarmLevel).map(r => r.alarmLevel))])
const snRules = computed(() => props.rules.filter(r => r.droneSn))

function setTypes(list) {
  for (const t of list) if (!checkedTypes.value.includes(t)) emit('add', { alarmType: t })
  for (const t of checkedTypes.value) if (!list.includes(t)) emit('remove', { alarmType: t })
}
function setLevels(list) {
  for (const l of list) if (!checkedLevels.value.includes(l)) emit('add', { alarmLevel: l })
  for (const l of checkedLevels.value) if (!list.includes(l)) emit('remove', { alarmLevel: l })
}
function addSn() {
  const v = newSn.value.trim()
  if (v) { emit('add', { droneSn: v }); newSn.value = '' }
}
</script>

<style scoped>
.suppress-group { margin-bottom: 16px; }
.suppress-group-title { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
</style>
