<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['next'])

const form = ref({
  requirement: props.task.requirement || '',
  description: props.task.description || '',
  sourceLang: props.task.sourceLang || undefined,
  targetLang: props.task.targetLang || undefined,
  parseGranularity: props.task.parseGranularity || 'SENTENCE',
  enableGlossary: !!props.task.enableGlossary,
  enableHistory: !!props.task.enableHistory,
  translateModel: props.task.translateModel || undefined,
  enableReview: !!props.task.enableReview,
  reviewModel: props.task.reviewModel || 'deepseek-chat',
  enableSummary: !!props.task.enableSummary
})

const langOptions = computed(() => store.languages.map((l) => ({ value: l.code, label: l.label })))
const modelOptions = computed(() => store.models.map((m) => ({ value: m, label: m })))
const granularityOptions = [
  { value: 'SENTENCE', label: '按句拆分（逐句翻译，更细粒度）' },
  { value: 'PARAGRAPH', label: '按段拆分（整段翻译，保持上下文）' }
]

// 上传文件
const uploadedFileName = ref(props.task.sourceFileName || '')
const uploading = ref(false)

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
      parseGranularity: form.value.parseGranularity,
      enableGlossary: form.value.enableGlossary,
      enableHistory: form.value.enableHistory,
      translateModel: form.value.translateModel,
      enableReview: form.value.enableReview,
      reviewModel: form.value.reviewModel,
      enableSummary: form.value.enableSummary
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

onMounted(loadGlossary)
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
            <a-form-item label="拆分粒度">
              <a-radio-group v-model:value="form.parseGranularity" option-type="button" button-style="solid">
                <a-radio-button v-for="g in granularityOptions" :key="g.value" :value="g.value">
                  {{ g.label }}
                </a-radio-button>
              </a-radio-group>
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
          </a-form>
        </a-card>

        <a-card title="待翻译文件" size="small" style="margin-top: 16px">
          <a-upload :before-upload="beforeUpload" :show-upload-list="false" accept=".txt,.docx,.html">
            <a-button :loading="uploading">
              <template #icon><UploadOutlined /></template>
              选择文件（.txt / .docx / .html）
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
