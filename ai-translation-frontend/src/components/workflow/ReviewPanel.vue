<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../../api.js'
import { store } from '../../store.js'
import { t } from '../../i18n.js'

const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['done', 'retranslate'])

const segments = ref([])
const loading = ref(false)
const saving = ref(false)

// 重新翻译
const showRetranslate = ref(false)
const retranslating = ref(false)
const modelOptions = computed(() => store.models.map((m) => ({ value: m, label: m })))
const retranslateForm = ref({
  model: props.task.translateModel || undefined,
  enableReview: !!props.task.enableReview,
  reviewModel: props.task.reviewModel || 'deepseek-v4-flash'
})

async function doRetranslate() {
  retranslating.value = true
  try {
    await api.startTranslate(props.task.id, {
      model: retranslateForm.value.model,
      enableReview: retranslateForm.value.enableReview,
      reviewModel: retranslateForm.value.reviewModel
    })
    message.success(t('taskStep.translate.started'))
    showRetranslate.value = false
    emit('retranslate')
  } catch (e) {
    message.error(e.message)
  } finally {
    retranslating.value = false
  }
}

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
  const target = blocks.find((el) => el.offsetTop <= mid && el.offsetTop + el.offsetHeight >= mid)
  if (target) highlight(target.dataset.seg)
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
    message.success(t('review.savedAll'))
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
    message.success(t('review.taskCompleted'))
    emit('done', 4)
  } catch (e) {
    message.error(e.message)
  }
}

defineExpose({ reload: loadSegments })

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
    <div class="review-toolbar">
      <a-typography-text type="secondary">{{ t('review.toolbar.hint') }}</a-typography-text>
      <a-space>
        <a-button @click="showRetranslate = true">{{ t('taskStep.translate.retranslate') }}</a-button>
        <a-button :loading="saving" @click="saveAll">{{ t('review.saveAll') }}</a-button>
        <a-button type="primary" :loading="saving" @click="confirmComplete">{{ t('review.confirmComplete') }}</a-button>
      </a-space>
    </div>

    <a-modal
      v-model:open="showRetranslate"
      :title="t('taskStep.translate.retranslate')"
      :confirm-loading="retranslating"
      :ok-text="t('taskStep.translate.retranslate.confirm')"
      @ok="doRetranslate"
    >
      <a-alert
        type="warning"
        show-icon
        style="margin-bottom: 16px"
        :message="t('taskStep.translate.retranslate.warning')"
      />
      <a-form layout="vertical">
        <a-form-item :label="t('taskStep.translate.model')">
          <a-select v-model:value="retranslateForm.model" :options="modelOptions" :placeholder="t('taskStep.translate.modelPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('taskStep.translate.enableReview')">
          <a-switch v-model:checked="retranslateForm.enableReview" />
        </a-form-item>
        <a-form-item v-if="retranslateForm.enableReview" :label="t('taskStep.translate.reviewModel')">
          <a-select v-model:value="retranslateForm.reviewModel" :options="modelOptions" :placeholder="t('taskStep.translate.modelPlaceholder')" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-row :gutter="16">
      <a-col :span="12">
        <div class="review-header">📄 {{ t('review.original') }}</div>
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
        <div class="review-header">✍️ {{ t('review.translation') }}</div>
        <div ref="rightRef" class="review-pane">
          <div v-for="seg in segments" :key="seg.id" class="review-seg" :data-seg="seg.orderNo">
            <div class="seg-meta">
              <a-tag>#{{ seg.orderNo }}</a-tag>
              <template v-if="seg.reviewScore != null">
                <a-tag :color="seg.reviewScore < 80 ? 'error' : 'success'">{{ t('review.score') }} {{ seg.reviewScore }}</a-tag>
                <a-tooltip v-if="seg.reviewAdvice" :title="seg.reviewAdvice">
                  <a-tag color="warning">{{ t('review.advice') }}</a-tag>
                </a-tooltip>
              </template>
            </div>
            <a-textarea
              v-model:value="seg.finalText"
              :auto-size="{ minRows: 1, maxRows: 8 }"
              @blur="saveSegment(seg)"
            />
            <div style="margin-top: 6px; text-align: right">
              <a @click="saveSegment(seg)">{{ t('review.saveSegment') }}</a>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-spin>
</template>
