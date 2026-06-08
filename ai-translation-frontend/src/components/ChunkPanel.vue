<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import { api } from '../api.js'

const STRATEGY_LABELS = {
  AUTO: '自动选择（按文件类型）',
  FIXED_SIZE: '固定长度',
  PARAGRAPH: '按段落',
  SENTENCE: '按句子',
  MARKDOWN: '按 Markdown 标题',
  TITLE: '按标题层级（Word/PDF）',
  HIERARCHICAL: '父子层级'
}

const ACCEPT = '.txt,.text,.md,.markdown,.html,.htm,.pdf,.doc,.docx'

const strategyOptions = ref([{ value: 'AUTO', label: STRATEGY_LABELS.AUTO }])
const form = ref({
  strategy: 'AUTO',
  chunkSize: 1000,
  overlap: 100,
  parentSize: 5000,
  childSize: 1000
})

const fileList = ref([])
const selectedFile = ref(null)
const loading = ref(false)
const result = ref(null)

// 单块字符数：仅固定长度策略生效
const showChunkSize = computed(() => form.value.strategy === 'FIXED_SIZE')
// 重叠字符数：固定长度 + 父子层级（含自动，可能命中超大文件层级拆分）生效
const showOverlap = computed(
  () => ['FIXED_SIZE', 'HIERARCHICAL', 'AUTO'].includes(form.value.strategy)
)
// 父块/子块字符数：父子层级（含自动）生效
const showHierarchicalParams = computed(
  () => form.value.strategy === 'AUTO' || form.value.strategy === 'HIERARCHICAL'
)

const COLORS = ['default', 'blue', 'orange', 'cyan', 'green', 'purple', 'magenta']

function levelTag(level) {
  const lv = level || 0
  // 标题策略：用 H{level} 表达标题层级；level=0 为标题前正文
  if (result.value?.strategy === 'TITLE') {
    return lv === 0 ? { text: '正文', color: 'default' } : { text: 'H' + lv, color: COLORS[lv] || 'blue' }
  }
  if (lv === 0) return { text: 'Root · 整篇', color: 'default' }
  if (lv === 2) return { text: '子块', color: 'cyan' }
  if (result.value?.strategy === 'HIERARCHICAL') return { text: '父块', color: 'orange' }
  return { text: '块', color: 'blue' }
}

function beforeUpload(file) {
  selectedFile.value = file
  fileList.value = [file]
  result.value = null
  return false
}

function onRemove() {
  selectedFile.value = null
  fileList.value = []
  result.value = null
}

async function loadStrategies() {
  try {
    const names = await api.getChunkStrategies()
    strategyOptions.value = [
      { value: 'AUTO', label: STRATEGY_LABELS.AUTO },
      ...names.map((n) => ({ value: n, label: STRATEGY_LABELS[n] || n }))
    ]
  } catch (e) {
    message.error(e.message)
  }
}

async function runChunk() {
  if (!selectedFile.value) {
    message.warning('请先选择要拆分的文档')
    return
  }
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('strategy', form.value.strategy)
  formData.append('chunkSize', form.value.chunkSize)
  formData.append('overlap', form.value.overlap)
  formData.append('parentSize', form.value.parentSize)
  formData.append('childSize', form.value.childSize)

  loading.value = true
  try {
    result.value = await api.previewChunk(formData)
    message.success(`拆分完成，共 ${result.value.chunkCount} 个块`)
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadStrategies)
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">文档拆分</h1>
      <p class="page-subtitle">上传文档、选择拆分方式，实时预览拆分后的分块效果</p>
    </div>

    <a-card title="拆分配置" size="small" style="margin-bottom: 16px">
      <a-upload-dragger
        :file-list="fileList"
        :accept="ACCEPT"
        :max-count="1"
        :before-upload="beforeUpload"
        @remove="onRemove"
      >
        <p class="ant-upload-drag-icon">
          <inbox-outlined />
        </p>
        <p class="ant-upload-text">点击或拖拽文件到此处</p>
        <p class="ant-upload-hint">支持 TXT / Markdown / HTML / PDF / Word（doc, docx）</p>
      </a-upload-dragger>

      <a-form layout="inline" class="chunk-form" style="margin-top: 16px">
        <a-form-item label="拆分方式">
          <a-select
            v-model:value="form.strategy"
            :options="strategyOptions"
            style="width: 220px"
          />
        </a-form-item>

        <a-form-item v-if="showChunkSize" label="单块字符数">
          <a-input-number v-model:value="form.chunkSize" :min="100" :step="100" style="width: 130px" />
        </a-form-item>

        <template v-if="showHierarchicalParams">
          <a-form-item label="父块字符数">
            <a-input-number v-model:value="form.parentSize" :min="500" :step="500" style="width: 130px" />
          </a-form-item>
          <a-form-item label="子块字符数">
            <a-input-number v-model:value="form.childSize" :min="100" :step="100" style="width: 130px" />
          </a-form-item>
        </template>

        <a-form-item v-if="showOverlap" label="重叠字符数">
          <a-input-number v-model:value="form.overlap" :min="0" :step="50" style="width: 130px" />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" :loading="loading" @click="runChunk">开始拆分</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-spin :spinning="loading">
      <a-card v-if="result" size="small">
        <template #title>
          拆分结果
          <a-tag color="orange" style="margin-left: 8px">
            {{ STRATEGY_LABELS[result.strategy] || result.strategy }}
          </a-tag>
        </template>

        <a-row :gutter="16" style="margin-bottom: 16px">
          <a-col :span="6"><a-statistic title="文件类型" :value="result.fileType" /></a-col>
          <a-col :span="6"><a-statistic title="块数量" :value="result.chunkCount" /></a-col>
          <a-col :span="6"><a-statistic title="总字符数" :value="result.totalChars" /></a-col>
          <a-col :span="6"><a-statistic title="估算 Token" :value="result.totalTokens" /></a-col>
        </a-row>

        <div class="chunk-list">
          <div
            v-for="chunk in result.chunks"
            :key="chunk.id"
            class="chunk-item"
            :style="{ marginLeft: (chunk.level || 0) * 20 + 'px' }"
          >
            <div class="chunk-meta">
              <a-tag :color="levelTag(chunk.level).color">{{ levelTag(chunk.level).text }}</a-tag>
              <span class="chunk-id">#{{ chunk.index }} · {{ chunk.id }}</span>
              <span v-if="chunk.title" class="chunk-title">{{ chunk.title }}</span>
              <span class="chunk-count">{{ chunk.charCount }} 字符 · ~{{ chunk.tokenCount }} token</span>
            </div>
            <pre class="chunk-content">{{ chunk.content }}</pre>
          </div>
        </div>
      </a-card>

      <a-empty v-else-if="!loading" description="尚无拆分结果，请上传文档并点击「开始拆分」" style="margin-top: 48px" />
    </a-spin>
  </div>
</template>

<style scoped>
.chunk-form :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}

.chunk-item {
  border: 1px solid #ece1d1;
  border-radius: 12px;
  background: #fffdf9;
  padding: 12px 14px;
}

.chunk-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #6f6253;
}

.chunk-id {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.chunk-title {
  font-weight: 600;
  color: #3d3329;
}

.chunk-count {
  margin-left: auto;
  color: #a08c74;
}

.chunk-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Nunito', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  line-height: 1.6;
  color: #3d3329;
  max-height: 220px;
  overflow-y: auto;
  background: #faf4ea;
  border-radius: 8px;
  padding: 10px 12px;
}
</style>
