<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'
import { t } from '../../i18n.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['next'])

// 文档拆分策略（与拆分引擎 ChunkStrategyType 对齐）
const STRATEGY_LABELS = {
  AUTO: '自动选择（按文件类型）',
  FIXED_SIZE: '固定长度',
  PARAGRAPH: '按段落',
  SENTENCE: '按句子',
  MARKDOWN: '按 Markdown 标题',
  TITLE: '按标题层级（Word/PDF）',
  HIERARCHICAL: '父子层级（超大文件）'
}
const ACCEPT = '.txt,.text,.md,.markdown,.html,.htm,.pdf,.doc,.docx'

function defaultChunkSize(strategy) {
  const s = strategy || 'AUTO'
  return ['TITLE', 'MARKDOWN', 'AUTO'].includes(s) ? 200 : 1000
}

const form = ref({
  requirement: props.task.requirement || '',
  description: props.task.description || '',
  sourceLang: props.task.sourceLang || undefined,
  targetLang: props.task.targetLang || undefined,
  chunkStrategy: props.task.chunkStrategy || 'AUTO',
  chunkSize: props.task.chunkSize ?? defaultChunkSize(props.task.chunkStrategy),
  chunkHeadingLevel: props.task.chunkHeadingLevel ?? 1,
  overlap: props.task.chunkOverlap || 100,
  parentSize: props.task.chunkParentSize || 5000,
  childSize: props.task.chunkChildSize || 1000,
  enableGlossary: !!props.task.enableGlossary,
  enableHistory: !!props.task.enableHistory,
  translateModel: props.task.translateModel || undefined,
  enableReview: !!props.task.enableReview,
  reviewModel: props.task.reviewModel || 'deepseek-v4-flash',
  enableSummary: !!props.task.enableSummary,
  enableImageTranslation: !!props.task.enableImageTranslation,
  imageTranslationModel: props.task.imageTranslationModel || ''
})

const langOptions = computed(() => store.languages.map((l) => ({ value: l.code, label: l.label })))
const modelOptions = computed(() => store.models.map((m) => ({ value: m, label: m })))

// 拆分策略下拉项（含 AUTO，其余由后端返回）
const strategyOptions = ref([{ value: 'AUTO', label: STRATEGY_LABELS.AUTO }])

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

// 上传文件（需在 isMarkdownContext 之前定义）
const uploadedFileName = ref(props.task.sourceFileName || '')
const uploading = ref(false)

// 单块/章节块字符数：固定长度、标题/Markdown 层级（含自动）生效
const isMarkdownContext = computed(() => {
  const s = form.value.chunkStrategy
  if (s === 'MARKDOWN') return true
  if (s === 'AUTO' && uploadedFileName.value) {
    return /\.(md|markdown)$/i.test(uploadedFileName.value)
  }
  return false
})

const showChunkSize = computed(() =>
  ['FIXED_SIZE', 'TITLE', 'MARKDOWN', 'AUTO'].includes(form.value.chunkStrategy)
)
const showHeadingLevel = computed(() => isMarkdownContext.value)

const headingLevelOptions = [1, 2, 3, 4, 5, 6].map((n) => ({
  value: n,
  label: 'H' + n
}))
const chunkSizeLabel = computed(() =>
  form.value.chunkStrategy === 'FIXED_SIZE' ? t('chunk.fixedSizeMax') : t('chunk.sectionMaxSize')
)
const chunkSizeMin = computed(() =>
  form.value.chunkStrategy === 'FIXED_SIZE' ? 100 : 50
)
// 重叠字符数：固定长度 + 父子层级（含自动，可能命中超大文件层级拆分）生效
const showOverlap = computed(() =>
  ['FIXED_SIZE', 'HIERARCHICAL', 'AUTO'].includes(form.value.chunkStrategy)
)
// 父块/子块字符数：父子层级（含自动）生效
const showHierarchicalParams = computed(
  () => form.value.chunkStrategy === 'AUTO' || form.value.chunkStrategy === 'HIERARCHICAL'
)

function beforeUpload(file) {
  doUpload(file)
  return false // 阻止 a-upload 自动上传，手动处理
}

async function doUpload(file) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await api.uploadTaskFile(props.task.id, fd)
    uploadedFileName.value = data?.fileName || file.name
    message.success('文件上传成功')
  } catch (e) {
    message.error(e.message)
  } finally {
    uploading.value = false
  }
}

// 临时术语
const glossaryList = ref([])
const glossaryLoading = ref(false)
const newTerm = ref({ term: '', translation: '' })
const adding = ref(false)

const glossaryColumns = [
  { title: '术语', dataIndex: 'term', key: 'term' },
  { title: '译文', dataIndex: 'translation', key: 'translation' },
  { title: '操作', key: 'action', width: 80 }
]

async function loadGlossary() {
  glossaryLoading.value = true
  try {
    glossaryList.value = await api.getTaskGlossary(props.task.id)
  } catch (e) {
    message.error(e.message)
  } finally {
    glossaryLoading.value = false
  }
}

async function addGlossary() {
  if (!newTerm.value.term.trim() || !newTerm.value.translation.trim()) {
    message.warning('术语和译文均不能为空')
    return
  }
  adding.value = true
  try {
    await api.addTaskGlossary(props.task.id, {
      term: newTerm.value.term,
      translation: newTerm.value.translation
    })
    newTerm.value = { term: '', translation: '' }
    await loadGlossary()
  } catch (e) {
    message.error(e.message)
  } finally {
    adding.value = false
  }
}

async function removeGlossary(record) {
  try {
    await api.deleteTaskGlossary(props.task.id, record.id)
    await loadGlossary()
  } catch (e) {
    message.error(e.message)
  }
}

// 保存配置并解析
const submitting = ref(false)

async function saveAndParse() {
  submitting.value = true
  try {
    await api.saveTaskConfig(props.task.id, {
      requirement: form.value.requirement,
      description: form.value.description,
      sourceLang: form.value.sourceLang,
      targetLang: form.value.targetLang,
      chunkStrategy: form.value.chunkStrategy,
      chunkSize: form.value.chunkSize,
      chunkHeadingLevel: form.value.chunkHeadingLevel,
      overlap: form.value.overlap,
      parentSize: form.value.parentSize,
      childSize: form.value.childSize,
      enableGlossary: form.value.enableGlossary,
      enableHistory: form.value.enableHistory,
      translateModel: form.value.translateModel,
      enableReview: form.value.enableReview,
      reviewModel: form.value.reviewModel,
      enableSummary: form.value.enableSummary,
      enableImageTranslation: form.value.enableImageTranslation,
      imageTranslationModel: form.value.imageTranslationModel
    })
    if (!uploadedFileName.value) {
      message.success('配置已保存，请上传待翻译文件后点击解析')
      return
    }
    await api.parseTask(props.task.id)
    message.success('配置已保存，开始解析')
    // 前往「解析与校对」步骤（不要跳过它）
    emit('next', 1)
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadGlossary()
  loadStrategies()
})
</script>

<template>
  <div>
    <a-row :gutter="24">
      <a-col :span="14">
        <a-card title="翻译配置" size="small">
          <a-form layout="vertical">
            <a-form-item label="客户要求">
              <a-textarea v-model:value="form.requirement" :rows="2" placeholder="客户对本次翻译的具体要求" />
            </a-form-item>
            <a-form-item label="任务描述">
              <a-textarea v-model:value="form.description" :rows="2" placeholder="可选" />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="源语种">
                  <a-select v-model:value="form.sourceLang" :options="langOptions" placeholder="选择源语种" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="目标语种">
                  <a-select v-model:value="form.targetLang" :options="langOptions" placeholder="选择目标语种" allow-clear />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="拆分方式">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-select
                    v-model:value="form.chunkStrategy"
                    :options="strategyOptions"
                    placeholder="选择拆分方式"
                  />
                </a-col>
                <a-col v-if="showChunkSize" :span="12">
                  <a-input-number
                    v-model:value="form.chunkSize"
                    :min="chunkSizeMin"
                    :step="100"
                    :addon-before="chunkSizeLabel"
                    style="width: 100%"
                  />
                </a-col>
              </a-row>
              <a-row v-if="showHeadingLevel" :gutter="12" style="margin-top: 10px">
                <a-col :span="12">
                  <a-form-item :label="t('chunk.headingLevel')" style="margin-bottom: 0">
                    <a-select
                      v-model:value="form.chunkHeadingLevel"
                      :options="headingLevelOptions"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row v-if="showHierarchicalParams || showOverlap" :gutter="12" style="margin-top: 10px">
                <a-col v-if="showHierarchicalParams" :span="8">
                  <a-input-number
                    v-model:value="form.parentSize"
                    :min="500"
                    :step="500"
                    addon-before="父块"
                    style="width: 100%"
                  />
                </a-col>
                <a-col v-if="showHierarchicalParams" :span="8">
                  <a-input-number
                    v-model:value="form.childSize"
                    :min="100"
                    :step="100"
                    addon-before="子块"
                    style="width: 100%"
                  />
                </a-col>
                <a-col v-if="showOverlap" :span="8">
                  <a-input-number
                    v-model:value="form.overlap"
                    :min="0"
                    :step="50"
                    addon-before="重叠"
                    style="width: 100%"
                  />
                </a-col>
              </a-row>
              <div style="margin-top: 6px">
                <a-typography-text type="secondary" style="font-size: 12px">
                  按所选策略将文档拆分为翻译单元，整块送翻以保留上下文。父子层级取最细粒度子块；自动选择会按文件类型与篇幅智能匹配策略。
                </a-typography-text>
              </div>
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="初翻译模型">
                  <a-select v-model:value="form.translateModel" :options="modelOptions" placeholder="选择模型" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="审校模型">
                  <a-select v-model:value="form.reviewModel" :options="modelOptions" placeholder="选择模型" allow-clear />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="6">
                <a-form-item label="启用术语库">
                  <a-switch v-model:checked="form.enableGlossary" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="历史数据优化">
                  <a-switch v-model:checked="form.enableHistory" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="AI 审校">
                  <a-switch v-model:checked="form.enableReview" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item>
                  <template #label>
                    <a-tooltip title="审校后对全文做术语 / 语气 / 人称一致性归一（汇总 Agent）">
                      风格统一
                    </a-tooltip>
                  </template>
                  <a-switch v-model:checked="form.enableSummary" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="8">
                <a-form-item>
                  <template #label>
                    <a-tooltip title="仅对 PDF 中解析出的图片生效；开启后会调用图片翻译模型并保存译后图片">
                      翻译 PDF 图片
                    </a-tooltip>
                  </template>
                  <a-switch v-model:checked="form.enableImageTranslation" />
                </a-form-item>
              </a-col>
              <a-col v-if="form.enableImageTranslation" :span="16">
                <a-form-item label="图片翻译模型">
                  <a-input
                    v-model:value="form.imageTranslationModel"
                    placeholder="例如：qwen-vl-plus / 你的多模态图片模型 code"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-card>

        <a-card title="待翻译文件" size="small" style="margin-top: 16px">
          <a-upload :before-upload="beforeUpload" :show-upload-list="false" :accept="ACCEPT">
            <a-button :loading="uploading">
              <template #icon><UploadOutlined /></template>
              选择文件（TXT / Markdown / HTML / PDF / Word）
            </a-button>
          </a-upload>
          <div v-if="uploadedFileName" style="margin-top: 10px">
            <a-tag color="success">已上传：{{ uploadedFileName }}</a-tag>
          </div>
        </a-card>
      </a-col>

      <a-col :span="10">
        <a-card title="临时术语" size="small">
          <a-space style="margin-bottom: 12px; width: 100%" direction="vertical">
            <a-input-group compact>
              <a-input v-model:value="newTerm.term" placeholder="术语" style="width: 40%" />
              <a-input v-model:value="newTerm.translation" placeholder="译文" style="width: 40%" />
              <a-button type="primary" :loading="adding" style="width: 20%" @click="addGlossary">添加</a-button>
            </a-input-group>
          </a-space>
          <a-table
            :columns="glossaryColumns"
            :data-source="glossaryList"
            :loading="glossaryLoading"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-popconfirm title="确定删除该术语吗？" @confirm="removeGlossary(record)">
                  <a style="color: #ff4d4f"><DeleteOutlined /></a>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <div style="margin-top: 24px; text-align: right">
      <a-button type="primary" :loading="submitting" @click="saveAndParse">保存配置并解析</a-button>
    </div>
  </div>
</template>
