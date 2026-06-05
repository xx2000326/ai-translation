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

const isParsing = computed(() => props.task.status === 'PARSING')
const isParsed = computed(() => props.task.status === 'PARSED')

async function loadSegments() {
  if (!isParsed.value) return
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
  if (ok) emit('next')
}

// 当从 PARSING 轮询转为 PARSED 时加载分段
watch(
  () => props.task.status,
  (val) => {
    if (val === 'PARSED' && segments.value.length === 0) {
      loadSegments()
    }
  }
)

onMounted(loadSegments)
</script>

<template>
  <div>
    <div v-if="isParsing" style="text-align: center; padding: 60px 0">
      <a-spin size="large" />
      <div style="margin-top: 16px; color: #888">正在解析文件，请稍候…</div>
    </div>

    <div v-else-if="isParsed">
      <a-typography-text type="secondary">
        共 {{ segments.length }} 句。可在此对原文分句进行人工校对修改。
      </a-typography-text>

      <a-spin :spinning="loading">
        <div class="seg-list" style="margin-top: 16px">
          <div v-for="seg in segments" :key="seg.id" class="seg-block">
            <div class="seg-meta">
              <a-tag>#{{ seg.orderNo }}</a-tag>
              <a-tag color="blue">{{ seg.blockType }}</a-tag>
            </div>
            <a-textarea v-model:value="seg.originalText" :auto-size="{ minRows: 1, maxRows: 6 }" />
          </div>
        </div>
      </a-spin>

      <div style="margin-top: 24px; text-align: right">
        <a-space>
          <a-button :loading="saving" @click="saveEdits">保存修改</a-button>
          <a-button type="primary" :loading="saving" @click="confirmAndTranslate">确认并进入翻译</a-button>
        </a-space>
      </div>
    </div>

    <a-empty v-else description="等待解析" />
  </div>
</template>
