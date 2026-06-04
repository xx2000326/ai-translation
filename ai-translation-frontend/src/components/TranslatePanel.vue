<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../api.js'
import { store } from '../store.js'

const text = ref('')
const style = ref(undefined)
const result = ref('')
const loading = ref(false)

const styleOptions = computed(() => store.styles.map((s) => ({ value: s, label: store.styleLabel(s) })))

async function doTranslate() {
  if (!text.value.trim()) {
    message.warning('请输入待翻译文本')
    return
  }
  loading.value = true
  result.value = ''
  try {
    // 简单翻译：不关联客户、不走术语库/记忆，只按风格翻译
    const data = await api.translate({
      customerId: null,
      text: text.value,
      role: null,
      style: style.value || null
    })
    result.value = data.translatedText
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

async function copyResult() {
  if (result.value) {
    await navigator.clipboard.writeText(result.value)
    message.success('已复制到剪贴板')
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <a-typography-title :level="4" style="margin: 0">翻译</a-typography-title>
      <a-typography-text type="secondary">快速翻译，支持中英互译，只需选择风格</a-typography-text>
    </div>

    <a-card :bordered="false" style="background: transparent">
      <a-space style="margin-bottom: 16px">
        <span style="color: #888">翻译风格</span>
        <a-select
          v-model:value="style"
          :options="styleOptions"
          placeholder="默认正式"
          allow-clear
          style="width: 200px"
        />
      </a-space>

      <a-row :gutter="16">
        <a-col :xs="24" :md="12">
          <div style="margin-bottom: 8px; font-weight: 600">原文</div>
          <a-textarea
            v-model:value="text"
            :rows="11"
            placeholder="请输入要翻译的文本，支持中英互译…"
          />
        </a-col>
        <a-col :xs="24" :md="12">
          <div style="margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center">
            <span style="font-weight: 600">译文</span>
            <a-button v-if="result" type="link" size="small" @click="copyResult">复制</a-button>
          </div>
          <div class="translate-result" :class="{ placeholder: !result }">
            {{ result || '翻译结果将显示在这里' }}
          </div>
        </a-col>
      </a-row>

      <a-button type="primary" size="large" style="margin-top: 16px" :loading="loading" @click="doTranslate">
        开始翻译
      </a-button>
    </a-card>
  </div>
</template>
