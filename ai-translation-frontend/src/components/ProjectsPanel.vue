<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../api.js'
import { store } from '../store.js'
import TranslationWorkflow from './workflow/TranslationWorkflow.vue'

const list = ref([])
const loading = ref(false)
const modalOpen = ref(false)
const submitting = ref(false)
const editingId = ref(null)

// 工作流视图：有 activeTask 时渲染工作流，否则渲染项目表格
const activeTask = ref(null)
const activeProjectName = ref('')
const starting = ref(false)

// projectId -> 最新任务，用于判断项目是否已启动过
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

const columns = [
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '关联客户', key: 'customer' },
  { title: '术语库', key: 'enableGlossary', width: 90 },
  { title: '角色', key: 'role', width: 110 },
  { title: '风格', key: 'style', width: 110 },
  { title: '操作', key: 'action', width: 200 }
]

function customerName(customerId) {
  const c = store.customers.find((x) => x.id === customerId)
  return c ? c.name : '-'
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
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center">
      <div>
        <a-typography-title :level="4" style="margin: 0">项目</a-typography-title>
        <a-typography-text type="secondary">关联客户与术语库配置，为后续的文件翻译做准备</a-typography-text>
      </div>
      <a-button type="primary" @click="openCreate">新增项目</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="{ pageSize: 10 }"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'customer'">
          {{ customerName(record.customerId) }}
        </template>
        <template v-else-if="column.key === 'enableGlossary'">
          <a-tag v-if="record.enableGlossary" color="success">启用</a-tag>
          <a-tag v-else color="default">关闭</a-tag>
        </template>
        <template v-else-if="column.key === 'role'">
          {{ store.roleLabel(record.role) }}
        </template>
        <template v-else-if="column.key === 'style'">
          {{ store.styleLabel(record.style) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a @click="enterProject(record)" :disabled="starting">
              {{ projectTaskMap[record.id] ? '进入项目' : '启动项目' }}
            </a>
            <a @click="openEdit(record)">编辑</a>
            <a-popconfirm title="确定删除该项目吗？" @confirm="removeProject(record)">
              <a style="color: #ff4d4f">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑项目' : '新增项目'"
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
        <a-form-item label="默认翻译角色">
          <a-select v-model:value="form.role" :options="roleOptions" placeholder="不设置" allow-clear />
        </a-form-item>
        <a-form-item label="默认翻译风格">
          <a-select v-model:value="form.style" :options="styleOptions" placeholder="不设置" allow-clear />
        </a-form-item>
        <a-form-item label="项目描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="可选" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
