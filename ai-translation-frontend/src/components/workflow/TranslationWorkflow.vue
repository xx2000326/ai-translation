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

// 进行中状态（需要轮询）。含翻译/审校的所有在途中间态（TRANSLATED / REVIEW_DONE），
// 保证从初翻到人工审校之间持续轮询，避免中间态被误判为停止。
const RUNNING_STATUS = ['PARSING', 'TRANSLATING', 'TRANSLATED', 'REVIEWING', 'REVIEW_DONE']
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

// 子步骤完成回调：刷新任务（refreshTask 会按状态前进步骤），并可由子组件指定一个"最小前进到"的目标步骤。
// 仅向前推进，绝不回退：避免出现「解析触发后直接跳到 AI 翻译」或「翻译极快时被拉回」的问题。
async function onStepNext(target) {
  await refreshTask()
  if (typeof target === 'number' && target > activeStep.value) {
    activeStep.value = Math.min(target, steps.length - 1)
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
          <StepReview v-else-if="activeStep === 3" :task="task" @done="onStepNext" />
          <StepExport v-else-if="activeStep === 4" :task="task" />
        </div>
      </a-spin>
    </div>
  </div>
</template>
