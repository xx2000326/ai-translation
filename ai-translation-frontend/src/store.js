import { reactive } from 'vue'
import { api } from './api.js'

/**
 * 全局共享状态：客户列表、角色/风格枚举。
 */
export const store = reactive({
  customers: [],
  roles: [],
  styles: [],
  models: [],
  languages: [],

  roleLabels: {
    professional: '专业文档',
    literature: '文学',
    advertisement: '广告'
  },
  styleLabels: {
    formal: '正式',
    concise: '简洁',
    creative: '创意',
    emotional: '情绪化'
  },

  async loadMeta() {
    const [roles, styles, models, languages] = await Promise.all([
      api.getRoles(),
      api.getStyles(),
      api.getModels(),
      api.getLanguages()
    ])
    this.roles = roles
    this.styles = styles
    this.models = models
    this.languages = languages
  },

  async loadModels() {
    this.models = await api.getModels()
  },

  async loadLanguages() {
    this.languages = await api.getLanguages()
  },

  async loadCustomers() {
    this.customers = await api.getCustomers()
  },

  roleLabel(code) {
    return this.roleLabels[code] || code || '-'
  },
  styleLabel(code) {
    return this.styleLabels[code] || code || '-'
  },
  langLabel(code) {
    const lang = this.languages.find((l) => l.code === code)
    return lang ? lang.label : code || '-'
  }
})
