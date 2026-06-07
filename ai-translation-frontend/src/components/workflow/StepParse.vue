<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['next'])

const segments = ref([])
const loading = ref(false)
const saving = ref(false)

// 解析结果已生成的所有状态（PARSED 及之后），用于"回看"已翻译/已完成任务的原文分段
const PARSE_AVAILABLE = ['PARSED', 'TRANSLATING', 'TRANSLATED', 'REVIEWING', 'REVIEW_DONE',
  'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED']

const isParsing = computed(() => props.task.status === 'PARSING')
// 是否已有解析结果可展示
const hasParseResult = computed(() => PARSE_AVAILABLE.includes(props.task.status))
// 仅在 PARSED 阶段允许编辑原文并触发翻译；后续状态为只读回看
const editable = computed(() => props.task.status === 'PARSED')

async function loadSegments() {
  if (!hasParseResult.value) return
  loading.value = true
  try {
    segments.value = await api.getSegments(props.task.id)
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function buildChangedPayload() {
  return segments.value.map((s) => ({ id: s.id, originalText: s.originalText }))
}

async function saveEdits() {
  saving.value = true
  try {
    await api.updateSentences(props.task.id, buildChangedPayload())
    message.success('原文修改已保存')
    return true
  } catch (e) {
    message.error(e.message)
    return false
  } finally {
    saving.value = false
  }
}

async function confirmAndTranslate() {
  const ok = await saveEdits()
  // 前往「AI 翻译」步骤（解析状态保持 PARSED，需显式前进）
  if (ok) emit('next', 2)
}

// 当从 PARSING 轮询转为 PARSED（或回看已完成任务）时加载分段
watch(
  () => props.task.status,
  () => {
    if (hasParseResult.value && segments.value.length === 0) {
      loadSegments()
    }
  }
)

onMounted(loadSegments)
</script>

<template>
  <div>
    <div v-if="isParsing" class="run-stage">
      <div class="run-emoji">📖</div>
      <a-spin size="large" style="margin-top: 12px" />
      <div class="run-text">正在解析文件</div>
      <div class="run-sub">正在拆分段落与句子，请稍候…</div>
    </div>

    <div v-else-if="hasParseResult">
      <a-typography-text type="secondary">
        共 {{ segments.length }} 句。{{ editable ? '可在此对原文分句进行人工校对修改。' : '当前任务已进入后续阶段，原文分段仅供回看。' }}
      </a-typography-text>

      <a-spin :spinning="loading">
        <div class="seg-list" style="margin-top: 16px">
          <div v-for="seg in segments" :key="seg.id" class="seg-block">
            <div class="seg-meta">
              <a-tag>#{{ seg.orderNo }}</a-tag>
              <a-tag color="blue">{{ seg.blockType }}</a-tag>
            </div>
            <a-textarea
              v-model:value="seg.originalText"
              :auto-size="{ minRows: 1, maxRows: 6 }"
              :readonly="!editable"
            />
          </div>
        </div>
      </a-spin>

      <div v-if="editable" style="margin-top: 24px; text-align: right">
        <a-space>
          <a-button :loading="saving" @click="saveEdits">保存修改</a-button>
          <a-button type="primary" :loading="saving" @click="confirmAndTranslate">确认并进入翻译</a-button>
        </a-space>
      </div>
    </div>

    <a-empty v-else description="等待解析" />
  </div>
</template>
