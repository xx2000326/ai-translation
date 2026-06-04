<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['next'])

const form = ref({
  model: props.task.translateModel || undefined,
  enableReview: !!props.task.enableReview,
  reviewModel: props.task.reviewModel || 'deepseek-chat'
})

const modelOptions = computed(() => store.models.map((m) => ({ value: m, label: m })))

const RUNNING = ['TRANSLATING', 'REVIEWING']
const isRunning = computed(() => RUNNING.includes(props.task.status))

const statusText = computed(() => {
  switch (props.task.status) {
    case 'TRANSLATING':
      return 'AI 翻译中…'
    case 'REVIEWING':
      return `AI 审校中，第 ${props.task.reviewRound || 1} 轮`
    case 'TRANSLATED':
      return '初翻完成'
    case 'REVIEW_DONE':
      return 'AI 审校完成'
    default:
      return ''
  }
})

const submitting = ref(false)

async function startTranslate() {
  submitting.value = true
  try {
    await api.startTranslate(props.task.id, {
      model: form.value.model,
      enableReview: form.value.enableReview,
      reviewModel: form.value.reviewModel
    })
    message.success('已开始 AI 翻译')
    emit('next')
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <div v-if="isRunning" style="text-align: center; padding: 60px 0">
      <a-spin size="large" />
      <div style="margin-top: 16px; color: #888">{{ statusText }}</div>
    </div>

    <a-card v-else title="AI 翻译设置" size="small" style="max-width: 520px">
      <a-form layout="vertical">
        <a-form-item label="初翻译模型">
          <a-select v-model:value="form.model" :options="modelOptions" placeholder="选择模型" />
        </a-form-item>
        <a-form-item label="AI 审校">
          <a-switch v-model:checked="form.enableReview" />
        </a-form-item>
        <a-form-item v-if="form.enableReview" label="审校模型">
          <a-select v-model:value="form.reviewModel" :options="modelOptions" placeholder="选择模型" />
        </a-form-item>
      </a-form>
      <div style="text-align: right">
        <a-button type="primary" :loading="submitting" @click="startTranslate">开始 AI 翻译</a-button>
      </div>
    </a-card>
  </div>
</template>
