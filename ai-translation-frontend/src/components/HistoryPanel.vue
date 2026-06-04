<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { api } from '../api.js'
import { store } from '../store.js'

const list = ref([])
const loading = ref(false)
const searchCustomer = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await api.getHistory(searchCustomer.value || undefined)
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center">
      <div>
        <a-typography-title :level="4" style="margin: 0">历史记录</a-typography-title>
        <a-typography-text type="secondary">所有客户的翻译历史</a-typography-text>
      </div>
      <a-space>
        <a-input-search
          v-model:value="searchCustomer"
          placeholder="按客户名搜索"
          style="width: 220px"
          allow-clear
          @search="load"
        />
        <a-button :loading="loading" @click="load">刷新</a-button>
      </a-space>
    </div>

    <a-list
      :data-source="list"
      :loading="loading"
      item-layout="vertical"
      :pagination="list.length > 5 ? { pageSize: 5 } : false"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-space style="margin-bottom: 8px">
            <a-tag v-if="item.customerName" color="blue">{{ item.customerName }}</a-tag>
            <a-tag color="processing">{{ store.roleLabel(item.role) }}</a-tag>
            <a-tag>{{ store.styleLabel(item.style) }}</a-tag>
            <span style="color: #999; font-size: 12px">{{ item.createTime }}</span>
          </a-space>
          <div class="history-original"><b>原文：</b>{{ item.originalText }}</div>
          <div class="history-translated"><b>译文：</b>{{ item.translatedText }}</div>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>
