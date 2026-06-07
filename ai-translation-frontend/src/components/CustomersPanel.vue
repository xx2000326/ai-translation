<script setup>
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../api.js'
import { store } from '../store.js'

const editingId = ref(null)
const submitting = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return { name: '', contact: '', defaultRole: undefined, defaultStyle: undefined, remark: '' }
}

const roleOptions = computed(() => store.roles.map((r) => ({ value: r, label: store.roleLabel(r) })))
const styleOptions = computed(() => store.styles.map((s) => ({ value: s, label: store.styleLabel(s) })))

const columns = [
  { title: '客户名', dataIndex: 'name', key: 'name' },
  { title: '联系方式', dataIndex: 'contact', key: 'contact' },
  { title: '默认角色', key: 'defaultRole' },
  { title: '默认风格', key: 'defaultStyle' },
  { title: '操作', key: 'action', width: 160 }
]

function resetForm() {
  editingId.value = null
  form.value = emptyForm()
}

function editCustomer(c) {
  editingId.value = c.id
  form.value = {
    name: c.name || '',
    contact: c.contact || '',
    defaultRole: c.defaultRole || undefined,
    defaultStyle: c.defaultStyle || undefined,
    remark: c.remark || ''
  }
}

async function submit() {
  if (!form.value.name.trim()) {
    message.warning('客户名不能为空')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await api.updateCustomer(editingId.value, form.value)
      message.success('已更新客户')
    } else {
      await api.createCustomer(form.value)
      message.success('已创建客户')
    }
    resetForm()
    await store.loadCustomers()
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function removeCustomer(c) {
  try {
    await api.deleteCustomer(c.id)
    message.success('已删除客户')
    if (editingId.value === c.id) resetForm()
    await store.loadCustomers()
  } catch (e) {
    message.error(e.message)
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">客户管理</h1>
      <p class="page-subtitle">维护客户档案与默认翻译偏好，作为项目与术语库的归属</p>
    </div>

    <a-row :gutter="16">
      <a-col :xs="24" :md="9">
        <a-card :title="editingId ? '编辑客户' : '新增客户'" size="small">
          <a-form layout="vertical">
            <a-form-item label="客户名" required>
              <a-input v-model:value="form.name" placeholder="如：某某科技有限公司" />
            </a-form-item>
            <a-form-item label="联系方式">
              <a-input v-model:value="form.contact" placeholder="电话 / 邮箱" />
            </a-form-item>
            <a-form-item label="默认翻译角色">
              <a-select v-model:value="form.defaultRole" :options="roleOptions" placeholder="不设置" allow-clear />
            </a-form-item>
            <a-form-item label="默认翻译风格">
              <a-select v-model:value="form.defaultStyle" :options="styleOptions" placeholder="不设置" allow-clear />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model:value="form.remark" :rows="2" placeholder="可选" />
            </a-form-item>
            <a-space>
              <a-button type="primary" :loading="submitting" @click="submit">
                {{ editingId ? '保存修改' : '创建客户' }}
              </a-button>
              <a-button v-if="editingId" @click="resetForm">取消</a-button>
            </a-space>
          </a-form>
        </a-card>
      </a-col>

      <a-col :xs="24" :md="15">
        <a-card :title="`客户列表（${store.customers.length}）`" size="small">
          <a-table
            :columns="columns"
            :data-source="store.customers"
            :pagination="false"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'contact'">
                {{ record.contact || '-' }}
              </template>
              <template v-else-if="column.key === 'defaultRole'">
                <a-tag color="processing">{{ store.roleLabel(record.defaultRole) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'defaultStyle'">
                <a-tag>{{ store.styleLabel(record.defaultStyle) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a @click="editCustomer(record)">编辑</a>
                  <a-popconfirm title="确定删除该客户吗？" @confirm="removeCustomer(record)">
                    <a style="color: #ff4d4f">删除</a>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
