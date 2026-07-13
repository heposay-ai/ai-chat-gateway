import api from './index'

// ========== W3 原有接口（向后兼容） ==========

export const uploadDocument = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/rag/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export const getDocuments = () => api.get('/rag/documents')

export const deleteDocument = (id) => api.delete(`/rag/documents/${id}`)

export const getDocumentProgress = (id) => api.get(`/rag/documents/${id}/progress`)

export const ragChat = (q, model) => api.get('/rag/chat', { params: { q, model } })

// ========== W4 知识库管理 ==========

export const createKb = (name, description) =>
  api.post('/rag/kb', { name, description })

export const listKbs = () => api.get('/rag/kb')

export const getKb = (id) => api.get(`/rag/kb/${id}`)

export const updateKb = (id, name, description) =>
  api.put(`/rag/kb/${id}`, { name, description })

export const deleteKb = (id) => api.delete(`/rag/kb/${id}`)

// ========== W4 知识库文档管理 ==========

export const uploadKbDocument = (kbId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post(`/rag/kb/${kbId}/documents/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export const listKbDocuments = (kbId) => api.get(`/rag/kb/${kbId}/documents`)

export const deleteKbDocument = (kbId, docId) =>
  api.delete(`/rag/kb/${kbId}/documents/${docId}`)

export const batchDeleteKbDocuments = (kbId, docIds) =>
  api.post(`/rag/kb/${kbId}/documents/batch-delete`, { docIds })

export const reindexKbDocument = (kbId, docId) =>
  api.post(`/rag/kb/${kbId}/documents/${docId}/reindex`)

export const importGitHubRepo = (kbId, url) =>
  api.post(`/rag/kb/${kbId}/documents/import-github`, { url })

// ========== W4 多轮对话 ==========

export const ragKbChat = (kbId, question, sessionId, model) =>
  api.post(`/rag/kb/${kbId}/chat`, { question, sessionId, model })

export const listSessions = (kbId) => api.get(`/rag/kb/${kbId}/sessions`)

export const getSessionMessages = (kbId, sessionId) =>
  api.get(`/rag/kb/${kbId}/sessions/${sessionId}/messages`)

export const deleteSession = (kbId, sessionId) =>
  api.delete(`/rag/kb/${kbId}/sessions/${sessionId}`)

// ========== W4 分享 ==========

export const toggleShare = (kbId, enabled) =>
  api.post(`/rag/kb/${kbId}/share`, null, { params: { enabled } })

// 分享链接接口（无需 API Key）
export const getSharedInfo = (token) =>
  api.get(`/rag/share/${token}/info`)

export const sharedChat = (token, question, model) =>
  api.post(`/rag/share/${token}/chat`, { question, model })

// ========== W4 RAG Debug ==========

export const ragDebug = (kbId, q, model) =>
  api.get(`/rag/kb/${kbId}/debug`, { params: { q, model } })
