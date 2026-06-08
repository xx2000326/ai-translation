<script setup>
import { ref, onMounted, computed, h } from 'vue'
import { message, theme as antdTheme } from 'ant-design-vue'
import {
  TranslationOutlined,
  ProjectOutlined,
  TeamOutlined,
  ProfileOutlined,
  HistoryOutlined,
  ScissorOutlined
} from '@ant-design/icons-vue'
import { store } from './store.js'
import TranslatePanel from './components/TranslatePanel.vue'
import ProjectsPanel from './components/ProjectsPanel.vue'
import CustomersPanel from './components/CustomersPanel.vue'
import GlossaryPanel from './components/GlossaryPanel.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import ChunkPanel from './components/ChunkPanel.vue'

const collapsed = ref(false)
const selectedKeys = ref(['translate'])

const menuItems = [
  { key: 'translate', icon: () => h(TranslationOutlined), label: '快速翻译' },
  { key: 'projects', icon: () => h(ProjectOutlined), label: '翻译项目' },
  { key: 'chunk', icon: () => h(ScissorOutlined), label: '文档拆分' },
  { key: 'customers', icon: () => h(TeamOutlined), label: '客户管理' },
  { key: 'glossary', icon: () => h(ProfileOutlined), label: '术语库' },
  { key: 'history', icon: () => h(HistoryOutlined), label: '翻译历史' }
]

const activeMenu = computed(() => selectedKeys.value[0])

// 温暖友好主题：陶土主色 + 奶油底 + 圆润字体
const warmTheme = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#d4724e',
    colorInfo: '#d4724e',
    colorSuccess: '#7c9473',
    colorWarning: '#e0a458',
    colorError: '#c8553d',
    colorTextBase: '#3d3329',
    colorText: '#3d3329',
    colorTextSecondary: '#6f6253',
    colorBorder: '#ece1d1',
    colorBorderSecondary: '#f3ebdd',
    colorBgLayout: 'transparent',
    colorBgContainer: '#fffdf9',
    borderRadius: 12,
    borderRadiusLG: 18,
    fontSize: 14,
    fontFamily:
      "'Nunito', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
    controlHeight: 38,
    wireframe: false
  },
  components: {
    Menu: {
      itemBg: 'transparent',
      itemColor: '#6f6253',
      itemHoverColor: '#c05e3b',
      itemSelectedColor: '#ffffff'
    },
    Steps: {
      colorPrimary: '#d4724e'
    },
    Card: {
      colorBgContainer: '#fffdf9'
    }
  }
}

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
  <a-config-provider :theme="warmTheme">
    <a-layout class="app-shell" style="min-height: 100vh">
      <a-layout-sider
        v-model:collapsed="collapsed"
        collapsible
        class="app-sider"
        :width="248"
        :trigger="null"
      >
        <div class="app-logo">
          <span class="logo-badge">语</span>
          <span v-if="!collapsed" class="logo-text">
            <span class="logo-title">语译</span>
            <span class="logo-sub">AI Translation</span>
          </span>
        </div>

        <a-menu v-model:selectedKeys="selectedKeys" mode="inline" :items="menuItems" />

        <div v-if="!collapsed" class="sider-footer">温暖、专业的 AI 翻译工作台</div>
      </a-layout-sider>

      <a-layout class="app-body">
        <a-layout-content>
          <div class="content-wrap">
            <TranslatePanel v-if="activeMenu === 'translate'" />
            <ProjectsPanel v-else-if="activeMenu === 'projects'" />
            <ChunkPanel v-else-if="activeMenu === 'chunk'" />
            <CustomersPanel v-else-if="activeMenu === 'customers'" />
            <GlossaryPanel v-else-if="activeMenu === 'glossary'" />
            <HistoryPanel v-else-if="activeMenu === 'history'" />
          </div>
        </a-layout-content>
      </a-layout>
    </a-layout>
  </a-config-provider>
</template>
