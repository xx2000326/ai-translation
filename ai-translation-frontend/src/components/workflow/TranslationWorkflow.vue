<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { api } from '../../api.js'
import StepConfig from './StepConfig.vue'
import StepParse from './StepParse.vue'
import StepTranslate from './StepTranslate.vue'
import StepReview from './StepReview.vue'
import StepExport from './StepExport.vue'

const props = defineProps({
  taskId: { type: [Number, String], required: true },
  projectName: { type: String, default: '' }
})
const emit = defineEmits(['back'])

const task = ref(null)
const loading = ref(false)
let timer = null

// 进行中状态（需要轮询）
const RUNNING_STATUS = ['PARSING', 'TRANSLATING', 'REVIEWING']
// 稳定态（停止轮询）
const STABLE_STATUS = ['PARSED', 'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED', 'FAILED']

// 状态 → 步骤映射
function statusToStep(status) {
  switch (status) {
    case 'DRAFT':
    case 'FILE_UPLOADED':
      return 0
    case 'PARSING':
    case 'PARSED':
      return 1
    case 'TRANSLATING':
    case 'TRANSLATED':
    case 'REVIEWING':
    case 'REVIEW_DONE':
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

// 默认步骤跟随状态，允许用户手动切换回看
const activeStep = ref(0)
const statusStep = computed(() => (task.value ? statusToStep(task.value.status) : 0))
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
    // 跟随状态推进（不强制覆盖用户手动回看到的更早步骤，但若状态前进则跟进）
    const target = statusToStep(data.status)
    if (target >= activeStep.value || RUNNING_STATUS.includes(data.status)) {
      activeStep.value = target
    }
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
      const target = statusToStep(data.status)
      if (target >= activeStep.value || RUNNING_STATUS.includes(data.status)) {
        activeStep.value = target
      }
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

// 子步骤完成回调：刷新任务并由状态映射决定步骤
// 若任务状态尚未推进（如 PARSED 后用户点击"确认并进入翻译"，状态不变），则强制前进一步
async function onStepNext() {
  await refreshTask()
  if (statusStep.value <= activeStep.value && activeStep.value < steps.length - 1) {
    activeStep.value += 1
  }
}

function onStepClick(e) {
  // 仅允许回看已完成或当前的步骤
  if (e <= statusStep.value) {
    activeStep.value = e
  }
}

onMounted(async () => {
  loading.value = true
  await refreshTask()
  loading.value = false
})

onUnmounted(stopPolling)
</script>

<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center">
      <div style="display: flex; align-items: center; gap: 12px">
        <a-button @click="emit('back')">
          <template #icon><ArrowLeftOutlined /></template>
          返回
        </a-button>
        <div>
          <a-typography-title :level="4" style="margin: 0">翻译工作流</a-typography-title>
          <a-typography-text type="secondary">
            {{ projectName || '项目' }} · 任务 #{{ taskId }}
          </a-typography-text>
        </div>
      </div>
    </div>

    <a-steps
      :current="activeStep"
      style="margin-bottom: 24px"
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

    <a-alert
      v-if="isFailed && task?.errorMsg"
      type="error"
      show-icon
      style="margin-bottom: 16px"
      message="任务执行失败"
      :description="task.errorMsg"
    />

    <a-spin :spinning="loading">
      <div v-if="task">
        <StepConfig v-if="activeStep === 0" :task="task" @next="onStepNext" />
        <StepParse v-else-if="activeStep === 1" :task="task" @next="onStepNext" />
        <StepTranslate v-else-if="activeStep === 2" :task="task" @next="onStepNext" />
        <StepReview v-else-if="activeStep === 3" :task="task" @done="onStepNext" />
        <StepExport v-else-if="activeStep === 4" :task="task" />
      </div>
    </a-spin>
  </div>
</template>
