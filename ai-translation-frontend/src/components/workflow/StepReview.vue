<script setup>
import ReviewPanel from './ReviewPanel.vue'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['done', 'retranslate'])
</script>

<template>
  <ReviewPanel :task="props.task" @done="(s) => emit('done', s)" @retranslate="emit('retranslate')" />
</template>
