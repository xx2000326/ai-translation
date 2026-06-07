<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SwapOutlined, CopyOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { api } from '../api.js'
import { store } from '../store.js'

const text = ref('')
const style = ref(undefined)
const result = ref('')
const loading = ref(false)

const styleOptions = computed(() => store.styles.map((s) => ({ value: s, label: store.styleLabel(s) })))

const charCount = computed(() => text.value.length)

async function doTranslate() {
  if (!text.value.trim()) {
    message.warning('请输入待翻译文本')
    return
  }
  loading.value = true
  result.value = ''
  try {
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

function clearText() {
  text.value = ''
  result.value = ''
}
</script>

<template>
  <div>
    <div class="translate-hero">
      <span class="hero-eyebrow"><ThunderboltOutlined /> 即时翻译</span>
      <h1 class="hero-title">让每一句话，都被温柔地理解</h1>
      <p class="page-subtitle">中英互译 · 多风格可选 · 由大模型驱动，无需任何配置即可开始</p>

      <div style="margin-top: 18px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
        <span style="color: var(--ink-soft); font-weight: 700">翻译风格</span>
        <a-select
          v-model:value="style"
          :options="styleOptions"
          placeholder="默认正式"
          allow-clear
          style="width: 200px"
        />
      </div>

      <a-row :gutter="20" style="margin-top: 20px">
        <a-col :xs="24" :md="12">
          <div class="trans-pane-label">
            <span>原文</span>
            <span style="font-weight: 500; color: var(--ink-faint); font-size: 12px">{{ charCount }} 字</span>
          </div>
          <div class="translate-box">
            <a-textarea
              v-model:value="text"
              :rows="11"
              :bordered="false"
              placeholder="在这里输入要翻译的文字，支持中英互译…"
            />
          </div>
        </a-col>
        <a-col :xs="24" :md="12">
          <div class="trans-pane-label">
            <span>译文</span>
            <a-button v-if="result" type="text" size="small" @click="copyResult">
              <template #icon><CopyOutlined /></template>
              复制
            </a-button>
          </div>
          <div class="translate-result" :class="{ placeholder: !result }">
            {{ result || '译文将温柔地出现在这里 ✦' }}
          </div>
        </a-col>
      </a-row>

      <div style="margin-top: 20px; display: flex; gap: 12px; align-items: center">
        <a-button type="primary" size="large" :loading="loading" @click="doTranslate">
          <template #icon><SwapOutlined /></template>
          开始翻译
        </a-button>
        <a-button size="large" @click="clearText">清空</a-button>
      </div>
    </div>
  </div>
</template>
