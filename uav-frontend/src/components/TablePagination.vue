<template>
  <div class="table-footer">
    <el-pagination
      :current-page="page"
      :page-size="size"
      :total="total"
      :page-sizes="sizes"
      :layout="layout"
      background
      @update:current-page="emit('update:page', $event)"
      @update:page-size="emit('update:size', $event)"
    />
  </div>
</template>

<script setup>
defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, required: true },
  layout: { type: String, default: 'total, prev, pager, next, sizes' },
  sizes: { type: Array, default: () => [10, 20, 50] },
})
const emit = defineEmits(['update:page', 'update:size'])
</script>
