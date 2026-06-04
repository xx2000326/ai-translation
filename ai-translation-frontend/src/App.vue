<script setup>
import { ref, onMounted, computed, h } from 'vue'
import { message } from 'ant-design-vue'
import {
  TranslationOutlined,
  ProjectOutlined,
  TeamOutlined,
  ProfileOutlined,
  HistoryOutlined
} from '@ant-design/icons-vue'
import { store } from './store.js'
import TranslatePanel from './components/TranslatePanel.vue'
import ProjectsPanel from './components/ProjectsPanel.vue'
import CustomersPanel from './components/CustomersPanel.vue'
import GlossaryPanel from './components/GlossaryPanel.vue'
import HistoryPanel from './components/HistoryPanel.vue'

const collapsed = ref(false)
const selectedKeys = ref(['translate'])

const menuItems = [
  { key: 'translate', icon: () => h(TranslationOutlined), label: '翻译' },
  { key: 'projects', icon: () => h(ProjectOutlined), label: '项目' },
  { key: 'customers', icon: () => h(TeamOutlined), label: '客户管理' },
  { key: 'glossary', icon: () => h(ProfileOutlined), label: '术语库' },
  { key: 'history', icon: () => h(HistoryOutlined), label: '历史记录' }
]

const activeMenu = computed(() => selectedKeys.value[0])

onMounted(async () => {
  try {
    await store.loadMeta()
    await store.loadCustomers()
  } catch (e) {
    message.error(e.message + '（请确认后端已启动并完成 MySQL/PGVector 配置）')
  }
})
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible theme="dark" :width="240">
      <div class="app-logo">
        <span class="logo-badge">译</span>
        <span v-if="!collapsed" class="logo-text">AI 翻译助手</span>
      </div>

      <a-menu v-model:selectedKeys="selectedKeys" theme="dark" mode="inline" :items="menuItems" />
    </a-layout-sider>

    <a-layout>
      <a-layout-content>
        <div class="content-wrap">
          <TranslatePanel v-if="activeMenu === 'translate'" />
          <ProjectsPanel v-else-if="activeMenu === 'projects'" />
          <CustomersPanel v-else-if="activeMenu === 'customers'" />
          <GlossaryPanel v-else-if="activeMenu === 'glossary'" />
          <HistoryPanel v-else-if="activeMenu === 'history'" />
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
