<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'
import { t } from '../../i18n.js'
import {
  buildReviewStepItems,
  computeReviewOverallPercent,
  reviewProgressLabel
} from '../../reviewProgressUtil.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['next'])

const form = ref({
  model: props.task.translateModel || undefined,
  enableReview: !!props.task.enableReview,
  reviewModel: props.task.reviewModel || 'deepseek-v4-flash'
})

const modelOptions = computed(() => store.models.map((m) => ({ value: m, label: m })))

// 翻译阶段的所有在途状态都视为"运行中"，避免在 TRANSLATED / REVIEW_DONE 等中间态
// 闪回到"开始 AI 翻译"设置卡片（翻译/审校/风格统一耗时较长时尤为明显）。
const RUNNING = ['TRANSLATING', 'TRANSLATED', 'REVIEWING', 'REVIEW_DONE']
const isRunning = computed(() => RUNNING.includes(props.task.status))

const statusText = computed(() => {
  if (props.task.progressPhase === 'SUMMARY' && props.task.enableSummary) {
    return t('summary.status.running')
  }
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

const TRANSLATE_DONE_STATUS = [
  'TRANSLATED', 'REVIEWING', 'REVIEW_DONE', 'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'
]

/** 运行中用任务配置，设置页用表单开关（勾选后立即展示审校进度条） */
const reviewEnabled = computed(() =>
  isRunning.value ? !!props.task.enableReview : !!form.value.enableReview
)

const summaryEnabled = computed(() => !!props.task.enableSummary)

const summaryProgress = computed(() => {
  if (!summaryEnabled.value) {
    return null
  }

  const status = props.task.status
  const phase = props.task.progressPhase || 'TRANSLATE'
  const total = props.task.totalSentences || 0
  const completed = props.task.completedSentences || 0

  if (['MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'].includes(status)) {
    return {
      percent: 100,
      barStatus: 'success',
      label: t('summary.progress.done')
    }
  }

  if (phase !== 'SUMMARY') {
    return {
      percent: 0,
      barStatus: 'normal',
      label: t('summary.progress.idle')
    }
  }

  const percent = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0
  return {
    percent,
    barStatus: percent >= 100 ? 'success' : 'active',
    label: total > 0 ? t('summary.progress', { completed, total }) : t('summary.progress.preparing')
  }
})

const translationProgress = computed(() => {
  const status = props.task.status
  const phase = props.task.progressPhase || 'TRANSLATE'
  const total = props.task.totalSentences || 0
  const completed = props.task.completedSentences || 0

  if (TRANSLATE_DONE_STATUS.includes(status)) {
    return {
      percent: 100,
      barStatus: 'success',
      label: t('translation.progress.done')
    }
  }

  if (status === 'TRANSLATING') {
    const inTranslatePhase = phase === 'TRANSLATE'
    const percent =
      inTranslatePhase && total > 0
        ? Math.min(100, Math.round((completed / total) * 100))
        : 0
    return {
      percent,
      barStatus: !inTranslatePhase || total <= 0 || percent < 100 ? 'active' : 'success',
      label:
        inTranslatePhase && total > 0
          ? t('translation.progress', { completed, total })
          : t('translation.progress.preparing')
    }
  }

  return {
    percent: 0,
    barStatus: 'normal',
    label: t('translation.progress.idle')
  }
})

const reviewProgress = computed(() => {
  if (!reviewEnabled.value) {
    return null
  }

  const status = props.task.status
  const phase = props.task.progressPhase || 'TRANSLATE'
  const round = props.task.reviewRound || 1
  const subPhase = props.task.reviewSubPhase || 'SCORING'
  const total = props.task.totalSentences || 0
  const completed = props.task.completedSentences || 0
  const reviewDone = phase === 'SUMMARY' ||
    ['REVIEW_DONE', 'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'].includes(status)

  const percent = computeReviewOverallPercent(status, round, subPhase, completed, total, phase)
  const barStatus =
    reviewDone
      ? 'success'
      : status === 'REVIEWING'
        ? percent >= 100
          ? 'success'
          : 'active'
        : 'normal'

  return {
    percent,
    barStatus,
    label: reviewProgressLabel(status, round, subPhase, completed, total, t, phase),
    steps: buildReviewStepItems(status, round, subPhase, t, phase)
  }
})

async function startTranslate() {
  submitting.value = true
  try {
    await api.startTranslate(props.task.id, {
      model: form.value.model,
      enableReview: form.value.enableReview,
      reviewModel: form.value.reviewModel
    })
    message.success('已开始 AI 翻译')
    // 停留在「AI 翻译」步骤展示进度；完成后由轮询自动推进到人工审校
    emit('next', 2)
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="step-translate">
    <div class="translate-progress-panel">
      <div class="progress-block">
        <div class="progress-block-title">{{ t('translation.progress.title') }}</div>
        <a-progress
          :percent="translationProgress.percent"
          :status="translationProgress.barStatus"
          :show-info="true"
        />
        <div class="translate-progress-label">{{ translationProgress.label }}</div>
      </div>
      <div v-if="reviewProgress" class="progress-block">
        <div class="progress-block-title">{{ t('review.progress.title') }}</div>
        <a-steps
          v-if="reviewProgress.steps?.length"
          size="small"
          :current="-1"
          class="review-segment-steps"
          :items="reviewProgress.steps"
        />
        <a-progress
          :percent="reviewProgress.percent"
          :status="reviewProgress.barStatus"
          :show-info="true"
        />
        <div class="translate-progress-label">{{ reviewProgress.label }}</div>
      </div>
      <div v-if="summaryProgress" class="progress-block">
        <div class="progress-block-title">{{ t('summary.progress.title') }}</div>
        <a-progress
          :percent="summaryProgress.percent"
          :status="summaryProgress.barStatus"
          :show-info="true"
        />
        <div class="translate-progress-label">{{ summaryProgress.label }}</div>
      </div>
    </div>

    <div v-if="isRunning" class="run-stage">
      <div class="run-emoji">{{ task.status === 'REVIEWING' ? '🔍' : '✨' }}</div>
      <a-spin size="large" style="margin-top: 12px" />
      <div class="run-text">{{ statusText }}</div>
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
