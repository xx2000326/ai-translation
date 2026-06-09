<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { api } from '../../api.js'
import StepConfig from './StepConfig.vue'
import StepParse from './StepParse.vue'
import StepTranslate from './StepTranslate.vue'
import StepReview from './StepReview.vue'
import StepExport from './StepExport.vue'
import { findStep, isAgentProcessing, STEP } from '../../taskStepUtil.js'

const props = defineProps({
  taskId: { type: [Number, String], required: true },
  projectName: { type: String, default: '' }
})
const emit = defineEmits(['back'])

const task = ref(null)
const loading = ref(false)
let timer = null

const RUNNING_STATUS = ['PARSING', 'AGENT_PROCESSING']
// 稳定态（停止轮询）
const STABLE_STATUS = ['PARSED', 'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED', 'FAILED']

// 状态 → 步骤映射（用于步骤条定位与可回看范围，不含自动跳转逻辑）
function statusToStep(status) {
  switch (status) {
    case 'DRAFT':
    case 'FILE_UPLOADED':
      return 0
    case 'PARSING':
    case 'PARSED':
      return 1
    case 'AGENT_PROCESSING':
      return 2
    case 'MANUAL_REVIEW':
      return 3
    case 'COMPLETED':
    case 'EXPORTED':
      return 4
    default:
      return 0
  }
}

const lastStatus = ref(null)
/** 本轮 AI 流程是否已自动进入过人工审校（防止回看时被再次拉回） */
const hasAutoAdvancedToReview = ref(false)

function isPastAiPhase(status, taskData) {
  if (status === 'MANUAL_REVIEW' || status === 'COMPLETED' || status === 'EXPORTED') {
    return true
  }

  const unifyStep = taskData ? findStep(taskData, STEP.SUMMARY_UNIFY) : null
  if (unifyStep && (unifyStep.status === 'RUNNING' || unifyStep.status === 'PENDING')) {
    return false
  }

  if (taskData?.enableSummary && isAgentProcessing(status)) {
    const translateStep = findStep(taskData, STEP.TRANSLATE)
    const scoreStep = findStep(taskData, STEP.REVIEW_SCORE)
    if (translateStep?.status === 'DONE' && (!scoreStep || scoreStep.status === 'DONE' || scoreStep.status === 'SKIPPED')) {
      if (unifyStep && unifyStep.status !== 'DONE' && unifyStep.status !== 'SKIPPED') {
        return false
      }
    }
  }

  return false
}

function applyStatusStep(data) {
  const status = data.status
  const prev = lastStatus.value
  const target = statusToStep(status)

  // 新一轮翻译开始，允许再次自动跳转
  if (status === 'AGENT_PROCESSING' && prev !== 'AGENT_PROCESSING') {
    hasAutoAdvancedToReview.value = false
  }

  if (status === 'COMPLETED' || status === 'EXPORTED') {
    activeStep.value = Math.max(activeStep.value, 4)
  } else if (isPastAiPhase(status, data) && !hasAutoAdvancedToReview.value) {
    activeStep.value = Math.max(activeStep.value, 3)
    hasAutoAdvancedToReview.value = true
  } else if (prev === null) {
    activeStep.value = Math.max(activeStep.value, target)
    if (isPastAiPhase(status, data)) {
      hasAutoAdvancedToReview.value = true
    }
  } else if (RUNNING_STATUS.includes(status) && !isPastAiPhase(status, data)) {
    if (target >= activeStep.value) {
      activeStep.value = target
    }
  }

  lastStatus.value = status
  syncMaxUnlockedStep(data)
}

function syncMaxUnlockedStep(data) {
  const target = statusToStep(data.status)
  maxUnlockedStep.value = Math.max(maxUnlockedStep.value, target)
  if (isPastAiPhase(data.status, data)) {
    maxUnlockedStep.value = Math.max(maxUnlockedStep.value, 3)
  }
}

// 默认步骤跟随状态，允许用户手动切换回看
const activeStep = ref(0)
/** 已解锁的最高可点击步骤（只增不减，回看时不封锁后续步骤） */
const maxUnlockedStep = ref(0)
const isFailed = computed(() => task.value?.status === 'FAILED')

const steps = [
  { title: '配置', description: '客户要求与文件' },
  { title: '解析与校对', description: '原文分段' },
  { title: 'AI 翻译', description: '模型翻译' },
  { title: '人工审校', description: '对照修订' },
  { title: '导出', description: '下载成稿' }
]

async function refreshTask() {
  try {
    const data = await api.getTask(props.taskId)
    task.value = data
    applyStatusStep(data)
    syncPolling()
  } catch (e) {
    message.error(e.message)
  }
}

function syncPolling() {
  const status = task.value?.status
  if (status && RUNNING_STATUS.includes(status)) {
    startPolling()
  } else {
    stopPolling()
  }
}

function startPolling() {
  if (timer) return
  timer = setInterval(async () => {
    try {
      const data = await api.getTask(props.taskId)
      task.value = data
      applyStatusStep(data)
      if (STABLE_STATUS.includes(data.status)) {
        stopPolling()
      }
    } catch (e) {
      message.error(e.message)
      stopPolling()
    }
  }, 2000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// 子步骤完成回调：刷新任务（refreshTask 会按状态前进步骤），并可由子组件指定一个"最小前进到"的目标步骤。
// 仅向前推进，绝不回退：避免出现「解析触发后直接跳到 AI 翻译」或「翻译极快时被拉回」的问题。
async function onStepNext(target) {
  await refreshTask()
  const step =
    typeof target === 'number' ? target : statusToStep(task.value?.status)
  if (step > activeStep.value) {
    activeStep.value = Math.min(step, steps.length - 1)
  }
}

function onStepClick(e) {
  if (e <= maxUnlockedStep.value) {
    activeStep.value = e
  }
}

// 人工审校页发起重新翻译：刷新任务并回到「AI 翻译」步骤展示重译进度
async function onRetranslate() {
  hasAutoAdvancedToReview.value = false
  await refreshTask()
  activeStep.value = 2
}

onMounted(async () => {
  loading.value = true
  await refreshTask()
  loading.value = false
})

watch(
  () => props.taskId,
  () => {
    lastStatus.value = null
    hasAutoAdvancedToReview.value = false
    maxUnlockedStep.value = 0
  }
)

onUnmounted(stopPolling)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="workflow-head">
        <a-button shape="circle" @click="emit('back')">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <div>
          <h1 class="page-title" style="font-size: 24px">{{ projectName || '翻译项目' }}</h1>
          <p class="page-subtitle">翻译工作流 · 任务 #{{ taskId }}</p>
        </div>
      </div>
    </div>

    <div class="workflow-card">
      <a-steps
        :current="activeStep"
        style="margin-bottom: 6px"
        :status="isFailed ? 'error' : 'process'"
        @change="onStepClick"
      >
        <a-step
          v-for="(s, i) in steps"
          :key="i"
          :title="s.title"
          :description="s.description"
        />
      </a-steps>
    </div>

    <a-alert
      v-if="isFailed && task?.errorMsg"
      type="error"
      show-icon
      style="margin: 16px 0"
      message="任务执行失败"
      :description="task.errorMsg"
    />

    <div class="workflow-card">
      <a-spin :spinning="loading">
        <div v-if="task">
          <StepConfig v-if="activeStep === 0" :task="task" @next="onStepNext" />
          <StepParse v-else-if="activeStep === 1" :task="task" @next="onStepNext" />
          <StepTranslate v-else-if="activeStep === 2" :task="task" @next="onStepNext" />
          <StepReview v-else-if="activeStep === 3" :task="task" @done="onStepNext" @retranslate="onRetranslate" />
          <StepExport v-else-if="activeStep === 4" :task="task" />
        </div>
      </a-spin>
    </div>
  </div>
</template>
