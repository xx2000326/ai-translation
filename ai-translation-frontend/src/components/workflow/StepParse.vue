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
const PARSE_AVAILABLE = ['PARSED', 'AGENT_PROCESSING', 'MANUAL_REVIEW', 'COMPLETED', 'EXPORTED']

const isParsing = computed(() => props.task.status === 'PARSING')
// 是否已有解析结果可展示
const hasParseResult = computed(() => PARSE_AVAILABLE.includes(props.task.status))
// 仅在 PARSED 阶段允许编辑原文并触发翻译；后续状态为只读回看
const editable = computed(() => props.task.status === 'PARSED')

// 含标题 / 层级信息的拆分（标题层级 / Markdown / 父子层级）按「章节块」层级化展示
const isStructure = computed(() =>
  segments.value.some((s) => s.level != null || s.title)
)
const unitLabel = computed(() => (isStructure.value ? '个章节块' : '个块'))

// 层级标签配色（按层级深度区分），与 ChunkPanel 预览页一致
const COLORS = ['default', 'blue', 'orange', 'cyan', 'green', 'purple', 'magenta']

// 根据分段层级 / 标题生成层级标签：标题层级显示 H{level}，父子层级显示父块 / 子块
function levelTag(seg) {
  if (seg.level == null) {
    return { text: '块', color: 'blue' }
  }
  if (seg.level === 0) {
    return { text: '正文', color: 'default' }
  }
  if (seg.title) {
    return { text: 'H' + seg.level, color: COLORS[seg.level] || 'blue' }
  }
  if (seg.level >= 2) {
    return { text: '子块', color: 'cyan' }
  }
  return { text: '父块', color: 'orange' }
}

// 按层级缩进，直观呈现父级 / 子级关系
function indentStyle(seg) {
  return { marginLeft: (seg.level || 0) * 20 + 'px' }
}

// 父级是否已作为独立行展示（标题层级 / Markdown 策略下父级本身就是一行）
function parentIsRow(seg) {
  return !!seg.parentTitle && segments.value.some((s) => s.title && s.title === seg.parentTitle)
}

// 是否在当前子块前插入父块分组表头（父子层级策略：父块不单独成行，按父级分组展示）
function showGroupHeader(seg, i) {
  if (!seg.parentTitle || parentIsRow(seg)) {
    return false
  }
  const prev = segments.value[i - 1]
  return !(prev && prev.parentTitle === seg.parentTitle)
}

// 父块分组表头缩进：比其子块少一级
function groupIndentStyle(seg) {
  const lv = seg.level ? Math.max(0, seg.level - 1) : 0
  return { marginLeft: lv * 20 + 'px' }
}

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
        共 {{ segments.length }} {{ unitLabel }}。{{ editable ? '可在此对原文分段进行人工校对修改。' : '当前任务已进入后续阶段，原文分段仅供回看。' }}
      </a-typography-text>

      <a-spin :spinning="loading">
        <div class="seg-list" style="margin-top: 16px">
          <template v-for="(seg, i) in segments" :key="seg.id">
            <div v-if="showGroupHeader(seg, i)" class="seg-group" :style="groupIndentStyle(seg)">
              <a-tag color="orange">父块</a-tag>
              <span class="seg-title">{{ seg.parentTitle }}</span>
            </div>
            <div class="seg-block" :style="indentStyle(seg)">
              <div class="seg-meta">
                <a-tag>#{{ seg.orderNo }}</a-tag>
                <a-tag :color="levelTag(seg).color">{{ levelTag(seg).text }}</a-tag>
                <span v-if="seg.title" class="seg-title">{{ seg.title }}</span>
                <a-typography-text v-if="parentIsRow(seg)" type="secondary" style="font-size: 12px">
                  上级：{{ seg.parentTitle }}
                </a-typography-text>
                <span class="seg-count">{{ (seg.originalText || '').length }} 字符</span>
              </div>
              <a-textarea
                v-model:value="seg.originalText"
                :auto-size="{ minRows: 1, maxRows: 8 }"
                :readonly="!editable"
              />
            </div>
          </template>
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
