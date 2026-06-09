<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'
import { t } from '../../i18n.js'
import {
  buildReviewStepItems,
  computeReviewOverallPercent,
  isReviewPhaseComplete,
  reviewProgressLabel
} from '../../reviewProgressUtil.js'
import {
  isAgentProcessing,
  isSummaryRunning,
  resolveReviewContext,
  resolveSummaryStep,
  resolveTranslateStep
} from '../../taskStepUtil.js'

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

const isRunning = computed(() => isAgentProcessing(props.task.status))

const statusText = computed(() => {
  if (isSummaryRunning(props.task)) {
    return t('summary.status.running')
  }
  const ctx = resolveReviewContext(props.task)
  if (ctx?.reviewing) {
    return t('taskStep.status.reviewing', { round: ctx.round })
  }
  const ts = resolveTranslateStep(props.task)
  if (isAgentProcessing(props.task.status) && ts.running) {
    return t('taskStep.status.translating')
  }
  return ''
})

const submitting = ref(false)

const reviewEnabled = computed(() =>
  isRunning.value ? !!props.task.enableReview : !!form.value.enableReview
)

const summaryEnabled = computed(() => !!props.task.enableSummary)

const summaryProgress = computed(() => {
  if (!summaryEnabled.value) {
    return null
  }

  const ss = resolveSummaryStep(props.task)
  if (!ss) {
    return null
  }

  if (['MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'].includes(props.task.status) || ss.done) {
    return {
      percent: 100,
      barStatus: 'success',
      label: t('summary.progress.done')
    }
  }

  if (!ss.running) {
    return {
      percent: 0,
      barStatus: 'normal',
      label: t('summary.progress.idle')
    }
  }

  const percent =
    ss.total > 0 ? Math.min(100, Math.round((ss.completed / ss.total) * 100)) : 0
  return {
    percent,
    barStatus: percent >= 100 ? 'success' : 'active',
    label:
      ss.total > 0
        ? t('summary.progress', { completed: ss.completed, total: ss.total })
        : t('summary.progress.preparing')
  }
})

const translationProgress = computed(() => {
  const status = props.task.status
  const ts = resolveTranslateStep(props.task)

  if (['MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'].includes(status)) {
    return {
      percent: 100,
      barStatus: 'success',
      label: t('translation.progress.done')
    }
  }

  if (isAgentProcessing(status)) {
    if (ts.done) {
      return {
        percent: 100,
        barStatus: 'success',
        label: t('translation.progress.done')
      }
    }
    if (ts.running && ts.total > 0) {
      const percent = Math.min(100, Math.round((ts.completed / ts.total) * 100))
      return {
        percent,
        barStatus: percent >= 100 ? 'success' : 'active',
        label: t('translation.progress', { completed: ts.completed, total: ts.total })
      }
    }
    if (ts.running) {
      return {
        percent: 0,
        barStatus: 'active',
        label: t('translation.progress.preparing')
      }
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

  const task = props.task
  const ctx = resolveReviewContext(task)
  const reviewDone = isReviewPhaseComplete(task)
  const reviewing = ctx.reviewing

  const percent = computeReviewOverallPercent(task)
  const barStatus = reviewDone
    ? 'success'
    : reviewing
      ? percent >= 100
        ? 'success'
        : 'active'
      : 'normal'

  return {
    percent,
    barStatus,
    label: reviewProgressLabel(task, t),
    steps: buildReviewStepItems(task, t)
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
    message.success(t('taskStep.translate.started'))
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
      <div class="run-emoji">{{ resolveReviewContext(task)?.reviewing ? '🔍' : '✨' }}</div>
      <a-spin size="large" style="margin-top: 12px" />
      <div class="run-text">{{ statusText }}</div>
    </div>

    <a-card v-else :title="t('taskStep.translate.settings')" size="small" style="max-width: 520px">
      <a-form layout="vertical">
        <a-form-item :label="t('taskStep.translate.model')">
          <a-select v-model:value="form.model" :options="modelOptions" :placeholder="t('taskStep.translate.modelPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('taskStep.translate.enableReview')">
          <a-switch v-model:checked="form.enableReview" />
        </a-form-item>
        <a-form-item v-if="form.enableReview" :label="t('taskStep.translate.reviewModel')">
          <a-select v-model:value="form.reviewModel" :options="modelOptions" :placeholder="t('taskStep.translate.modelPlaceholder')" />
        </a-form-item>
      </a-form>
      <div style="text-align: right">
        <a-button type="primary" :loading="submitting" @click="startTranslate">
          {{ t('taskStep.translate.start') }}
        </a-button>
      </div>
    </a-card>
  </div>
</template>
