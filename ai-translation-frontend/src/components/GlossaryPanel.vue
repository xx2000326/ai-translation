<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../api.js'
import { store } from '../store.js'

const list = ref([])
const searchCustomer = ref('')
const searchTerm = ref('')
const loading = ref(false)
const submitting = ref(false)
const form = ref({ customerId: undefined, term: '', translation: '', category: '' })

const customerOptions = computed(() =>
  store.customers.map((c) => ({ value: c.id, label: c.name }))
)

const columns = [
  { title: '客户', dataIndex: 'customerName', key: 'customerName' },
  { title: '术语', dataIndex: 'term', key: 'term' },
  { title: '译文', dataIndex: 'translation', key: 'translation' },
  { title: '分类', dataIndex: 'category', key: 'category' }
]

async function load() {
  loading.value = true
  try {
    list.value = await api.getGlossary(
      searchCustomer.value || undefined,
      searchTerm.value || undefined
    )
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!form.value.customerId) {
    message.warning('请选择客户')
    return
  }
  if (!form.value.term.trim() || !form.value.translation.trim()) {
    message.warning('术语与译文不能为空')
    return
  }
  submitting.value = true
  try {
    await api.addGlossary({
      customerId: form.value.customerId,
      term: form.value.term,
      translation: form.value.translation,
      category: form.value.category
    })
    form.value = { customerId: undefined, term: '', translation: '', category: '' }
    message.success('添加成功')
    await load()
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (store.customers.length === 0) {
    try {
      await store.loadCustomers()
    } catch (e) {
      message.error(e.message)
    }
  }
  await load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <a-typography-title :level="4" style="margin: 0">术语库</a-typography-title>
      <a-typography-text type="secondary">所有客户的术语库</a-typography-text>
    </div>

    <a-card title="新增术语" size="small" style="margin-bottom: 16px">
      <a-form layout="inline">
        <a-form-item label="客户">
          <a-select
            v-model:value="form.customerId"
            :options="customerOptions"
            placeholder="选择客户"
            style="width: 200px"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="术语">
          <a-input v-model:value="form.term" placeholder="如：machine learning" style="width: 180px" />
        </a-form-item>
        <a-form-item label="译文">
          <a-input v-model:value="form.translation" placeholder="如：机器学习" style="width: 180px" />
        </a-form-item>
        <a-form-item label="分类">
          <a-input v-model:value="form.category" placeholder="可选" style="width: 120px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="submitting" @click="add">添加术语</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :title="`术语列表（${list.length}）`" size="small">
      <template #extra>
        <a-space>
          <a-input
            v-model:value="searchCustomer"
            placeholder="按客户名搜索"
            style="width: 180px"
            allow-clear
            @press-enter="load"
          />
          <a-input-search
            v-model:value="searchTerm"
            placeholder="按术语搜索"
            style="width: 200px"
            @search="load"
          />
        </a-space>
      </template>
      <a-table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        :pagination="{ pageSize: 10 }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'customerName'">
            {{ record.customerName || '-' }}
          </template>
          <template v-else-if="column.key === 'category'">
            {{ record.category || '-' }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
