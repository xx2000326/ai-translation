<script setup>
import { ref } from 'vue'
import { DownloadOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  task: { type: Object, required: true }
})

const format = ref('txt')

function download() {
  const url = `/api/tasks/${props.task.id}/export?format=${format.value}`
  const a = document.createElement('a')
  a.href = url
  a.target = '_blank'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}
</script>

<template>
  <a-result status="success" title="翻译任务已完成" sub-title="选择导出格式下载最终成稿">
    <template #extra>
      <div style="display: flex; flex-direction: column; align-items: center; gap: 16px">
        <a-radio-group v-model:value="format" button-style="solid">
          <a-radio-button value="txt">TXT</a-radio-button>
          <a-radio-button value="html">HTML</a-radio-button>
        </a-radio-group>
        <a-button type="primary" @click="download">
          <template #icon><DownloadOutlined /></template>
          导出下载
        </a-button>
      </div>
    </template>
  </a-result>
</template>
