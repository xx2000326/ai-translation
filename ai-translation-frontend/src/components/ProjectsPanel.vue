<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, ArrowRightOutlined } from '@ant-design/icons-vue'
import { api } from '../api.js'
import { store } from '../store.js'
import TranslationWorkflow from './workflow/TranslationWorkflow.vue'

const list = ref([])
const loading = ref(false)
const modalOpen = ref(false)
const submitting = ref(false)
const editingId = ref(null)

const activeTask = ref(null)
const activeProjectName = ref('')
const starting = ref(false)

const projectTaskMap = ref({})

async function loadProjectTasks() {
  if (!list.value.length) return
  const results = await Promise.allSettled(list.value.map((p) => api.getTasksByProject(p.id)))
  const map = {}
  results.forEach((result, i) => {
    if (result.status === 'fulfilled' && result.value && result.value.length > 0) {
      const latest = result.value.reduce((a, b) => (b.id > a.id ? b : a))
      map[list.value[i].id] = latest
    }
  })
  projectTaskMap.value = map
}

async function enterProject(record) {
  starting.value = true
  try {
    const existingTask = projectTaskMap.value[record.id]
    let task
    if (existingTask) {
      task = await api.getTask(existingTask.id)
    } else {
      task = await api.startProject(record.id)
    }
    activeTask.value = task
    activeProjectName.value = record.name
  } catch (e) {
    message.error(e.message)
  } finally {
    starting.value = false
  }
}

async function backFromWorkflow() {
  activeTask.value = null
  activeProjectName.value = ''
  await load()
}

const form = ref(emptyForm())

function emptyForm() {
  return {
    name: '',
    customerId: undefined,
    enableGlossary: false,
    role: undefined,
    style: undefined,
    description: ''
  }
}

const customerOptions = computed(() => store.customers.map((c) => ({ value: c.id, label: c.name })))
const roleOptions = computed(() => store.roles.map((r) => ({ value: r, label: store.roleLabel(r) })))
const styleOptions = computed(() => store.styles.map((s) => ({ value: s, label: store.styleLabel(s) })))

function customerName(customerId) {
  const c = store.customers.find((x) => x.id === customerId)
  return c ? c.name : '未关联客户'
}

async function load() {
  loading.value = true
  try {
    list.value = await api.getProjects()
    await loadProjectTasks()
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  modalOpen.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    name: record.name,
    customerId: record.customerId ?? undefined,
    enableGlossary: !!record.enableGlossary,
    role: record.role || undefined,
    style: record.style || undefined,
    description: record.description || ''
  }
  modalOpen.value = true
}

async function submit() {
  if (!form.value.name.trim()) {
    message.warning('项目名称不能为空')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await api.updateProject(editingId.value, form.value)
      message.success('已更新项目')
    } else {
      await api.createProject(form.value)
      message.success('已创建项目')
    }
    modalOpen.value = false
    await load()
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function removeProject(record) {
  try {
    await api.deleteProject(record.id)
    message.success('已删除项目')
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <TranslationWorkflow
    v-if="activeTask"
    :task-id="activeTask.id"
    :project-name="activeProjectName"
    @back="backFromWorkflow"
  />
  <div v-else>
    <div class="page-header page-header-flex">
      <div>
        <h1 class="page-title">翻译项目</h1>
        <p class="page-subtitle">为客户与术语库配置专属工作流，开启文件级 AI 翻译</p>
      </div>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建项目
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!loading && list.length === 0" description="还没有项目，先新建一个吧">
        <a-button type="primary" @click="openCreate">新建项目</a-button>
      </a-empty>

      <div v-else class="project-grid stagger">
        <div
          v-for="p in list"
          :key="p.id"
          class="project-card"
          @click="enterProject(p)"
        >
          <div class="pc-name">{{ p.name }}</div>
          <div class="pc-desc">{{ p.description || '暂无描述' }}</div>
          <div class="pc-meta">
            <span class="meta-chip">{{ customerName(p.customerId) }}</span>
            <a-tag v-if="p.enableGlossary" color="success">术语库</a-tag>
            <a-tag v-if="p.role" color="warning">{{ store.roleLabel(p.role) }}</a-tag>
            <a-tag v-if="p.style">{{ store.styleLabel(p.style) }}</a-tag>
          </div>
          <div class="pc-actions">
            <a-button type="primary" size="small" :loading="starting" @click.stop="enterProject(p)">
              {{ projectTaskMap[p.id] ? '进入项目' : '启动项目' }}
              <template #icon><ArrowRightOutlined /></template>
            </a-button>
            <a-space>
              <a-button type="text" size="small" @click.stop="openEdit(p)">
                <template #icon><EditOutlined /></template>
              </a-button>
              <a-popconfirm title="确定删除该项目吗？" @confirm="removeProject(p)">
                <a-button type="text" size="small" danger @click.stop>
                  <template #icon><DeleteOutlined /></template>
                </a-button>
              </a-popconfirm>
            </a-space>
          </div>
        </div>

        <div class="project-add-card" @click="openCreate">
          <PlusOutlined style="font-size: 22px" />
          <span>新建项目</span>
        </div>
      </div>
    </a-spin>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑项目' : '新建项目'"
      :confirm-loading="submitting"
      @ok="submit"
    >
      <a-form layout="vertical">
        <a-form-item label="项目名称" required>
          <a-input v-model:value="form.name" placeholder="如：产品手册翻译" />
        </a-form-item>
        <a-form-item label="关联客户">
          <a-select v-model:value="form.customerId" :options="customerOptions" placeholder="选择客户" allow-clear />
        </a-form-item>
        <a-form-item label="启用术语库">
          <a-switch v-model:checked="form.enableGlossary" />
          <a-typography-text type="secondary" style="margin-left: 10px">
            启用后该项目翻译会注入所选客户的术语规则
          </a-typography-text>
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="默认翻译角色">
              <a-select v-model:value="form.role" :options="roleOptions" placeholder="不设置" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="默认翻译风格">
              <a-select v-model:value="form.style" :options="styleOptions" placeholder="不设置" allow-clear />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="项目描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="可选" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
