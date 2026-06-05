<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['done'])

const segments = ref([])
const loading = ref(false)
const saving = ref(false)

const leftRef = ref(null)
const rightRef = ref(null)

async function loadSegments() {
  loading.value = true
  try {
    const list = await api.getSegments(props.task.id)
    segments.value = list.map((s) => ({
      ...s,
      finalText: s.finalText || s.reviewedText || s.translatedText || ''
    }))
    await nextTick()
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

// 滚动/悬停高亮联动
function highlight(seg) {
  document.querySelectorAll('.seg-highlight').forEach((e) => e.classList.remove('seg-highlight'))
  const r = rightRef.value?.querySelector(`[data-seg="${seg}"]`)
  if (r) {
    r.classList.add('seg-highlight')
  }
}

function onLeftScroll() {
  const c = leftRef.value
  if (!c) return
  const mid = c.scrollTop + c.clientHeight / 2
  const blocks = [...c.querySelectorAll('[data-seg]')]
  const t = blocks.find((el) => el.offsetTop <= mid && el.offsetTop + el.offsetHeight >= mid)
  if (t) highlight(t.dataset.seg)
}

function onHoverLeft(orderNo) {
  highlight(orderNo)
}

async function saveSegment(seg) {
  try {
    await api.saveSentenceFinal(props.task.id, seg.id, { finalText: seg.finalText })
  } catch (e) {
    message.error(e.message)
  }
}

async function saveAll() {
  saving.value = true
  try {
    for (const seg of segments.value) {
      await api.saveSentenceFinal(props.task.id, seg.id, { finalText: seg.finalText })
    }
    message.success('全部译文已保存')
    return true
  } catch (e) {
    message.error(e.message)
    return false
  } finally {
    saving.value = false
  }
}

async function confirmComplete() {
  const ok = await saveAll()
  if (!ok) return
  try {
    await api.completeTask(props.task.id)
    message.success('任务已完成')
    emit('done')
  } catch (e) {
    message.error(e.message)
  }
}

onMounted(() => {
  loadSegments().then(() => {
    leftRef.value?.addEventListener('scroll', onLeftScroll)
  })
})

onUnmounted(() => {
  leftRef.value?.removeEventListener('scroll', onLeftScroll)
})
</script>

<template>
  <a-spin :spinning="loading">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
      <a-typography-text type="secondary">左侧原文，右侧译文（可编辑）。滚动或悬停左侧会高亮右侧对应段。</a-typography-text>
      <a-space>
        <a-button :loading="saving" @click="saveAll">保存全部</a-button>
        <a-button type="primary" :loading="saving" @click="confirmComplete">确认完成</a-button>
      </a-space>
    </div>

    <a-row :gutter="16">
      <a-col :span="12">
        <div class="review-header">原文</div>
        <div ref="leftRef" class="review-pane">
          <div
            v-for="seg in segments"
            :key="seg.id"
            class="review-seg"
            :data-seg="seg.orderNo"
            @mouseenter="onHoverLeft(seg.orderNo)"
          >
            <div class="seg-meta">
              <a-tag>#{{ seg.orderNo }}</a-tag>
              <a-tag color="blue">{{ seg.blockType }}</a-tag>
            </div>
            <div class="seg-original">{{ seg.originalText }}</div>
          </div>
        </div>
      </a-col>

      <a-col :span="12">
        <div class="review-header">译文</div>
        <div ref="rightRef" class="review-pane">
          <div v-for="seg in segments" :key="seg.id" class="review-seg" :data-seg="seg.orderNo">
            <div class="seg-meta">
              <a-tag>#{{ seg.orderNo }}</a-tag>
              <template v-if="seg.reviewScore != null">
                <a-tag :color="seg.reviewScore < 80 ? 'error' : 'success'">评分 {{ seg.reviewScore }}</a-tag>
                <a-tooltip v-if="seg.reviewAdvice" :title="seg.reviewAdvice">
                  <a-tag color="warning">审校建议</a-tag>
                </a-tooltip>
              </template>
            </div>
            <a-textarea
              v-model:value="seg.finalText"
              :auto-size="{ minRows: 1, maxRows: 8 }"
              @blur="saveSegment(seg)"
            />
            <div style="margin-top: 6px; text-align: right">
              <a @click="saveSegment(seg)">保存本段</a>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-spin>
</template>
