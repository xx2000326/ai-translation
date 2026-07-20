import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 统一拆包 Result 结构
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined') {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const api = {
  // 翻译
  translate: (payload) => http.post('/translate', payload),
  getRoles: () => http.get('/roles'),
  getStyles: () => http.get('/styles'),

  // 客户
  getCustomers: () => http.get('/customers'),
  createCustomer: (payload) => http.post('/customers', payload),
  updateCustomer: (id, payload) => http.put(`/customers/${id}`, payload),
  deleteCustomer: (id) => http.delete(`/customers/${id}`),

  // 项目（为后续文件翻译做准备）
  getProjects: (customerId) => http.get('/projects', { params: { customerId } }),
  createProject: (payload) => http.post('/projects', payload),
  updateProject: (id, payload) => http.put(`/projects/${id}`, payload),
  deleteProject: (id) => http.delete(`/projects/${id}`),

  // 术语库（按客户名搜索）
  getGlossary: (customerName, term) => http.get('/glossary', { params: { customerName, term } }),
  addGlossary: (payload) => http.post('/glossary', payload),

  // 历史（按客户名搜索）
  getHistory: (customerName) => http.get('/history', { params: { customerName } }),

  // 翻译任务工作流
  startProject: (projectId) => http.post(`/projects/${projectId}/start`),
  getTask: (id) => http.get(`/tasks/${id}`),
  getTasksByProject: (projectId) => http.get('/tasks', { params: { projectId } }),
  saveTaskConfig: (id, payload) => http.post(`/tasks/${id}/config`, payload),
  uploadTaskFile: (id, formData) => http.post(`/tasks/${id}/file`, formData),
  addTaskGlossary: (id, payload) => http.post(`/tasks/${id}/glossary`, payload),
  getTaskGlossary: (id) => http.get(`/tasks/${id}/glossary`),
  deleteTaskGlossary: (id, gid) => http.delete(`/tasks/${id}/glossary/${gid}`),
  parseTask: (id) => http.post(`/tasks/${id}/parse`),
  getParagraphs: (id) => http.get(`/tasks/${id}/paragraphs`),
  getSegments: (id) => http.get(`/tasks/${id}/segments`),
  getTaskImages: (id) => http.get(`/tasks/${id}/images`),
  updateSentences: (id, list) => http.put(`/tasks/${id}/sentences`, list),
  startTranslate: (id, payload) => http.post(`/tasks/${id}/translate`, payload),
  saveSentenceFinal: (taskId, sentenceId, payload) => http.put(`/tasks/${taskId}/sentences/${sentenceId}/final`, payload),
  completeTask: (id) => http.post(`/tasks/${id}/complete`),
  getModels: () => http.get('/models'),
  getLanguages: () => http.get('/languages'),

  // 文档拆分预览
  getChunkStrategies: () => http.get('/chunk/strategies'),
  previewChunk: (formData) => http.post('/chunk/preview', formData)
}
