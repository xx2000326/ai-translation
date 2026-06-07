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
    <div class="page-header page-header-flex">
      <div>
        <h1 class="page-title">翻译历史</h1>
        <p class="page-subtitle">回溯所有客户的快速翻译记录</p>
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
      :pagination="list.length > 6 ? { pageSize: 6 } : false"
    >
      <template #renderItem="{ item }">
        <div class="history-item">
          <a-space style="margin-bottom: 8px" wrap>
            <a-tag v-if="item.customerName" color="warning">{{ item.customerName }}</a-tag>
            <a-tag color="processing">{{ store.roleLabel(item.role) }}</a-tag>
            <a-tag>{{ store.styleLabel(item.style) }}</a-tag>
            <span style="color: var(--ink-faint); font-size: 12px">{{ item.createTime }}</span>
          </a-space>
          <div class="history-original"><b>原文：</b>{{ item.originalText }}</div>
          <div class="history-translated"><b>译文：</b>{{ item.translatedText }}</div>
        </div>
      </template>
    </a-list>
  </div>
</template>
