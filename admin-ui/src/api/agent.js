import api from './index'

// ========== Agent CRUD ==========

export const listAgents = () => api.get('/agents')

export const getAgent = (id) => api.get(`/agents/${id}`)

export const createAgent = (data) => api.post('/agents', data)

export const updateAgent = (id, data) => api.put(`/agents/${id}`, data)

export const deleteAgent = (id) => api.delete(`/agents/${id}`)

// ========== Agent 模板 ==========

export const listTemplates = () => api.get('/agents/templates')

export const cloneTemplate = (templateId) => api.post(`/agents/${templateId}/clone`)

// ========== 可用工具 ==========

export const listTools = () => api.get('/agents/tools')

// ========== Agent 会话 ==========

export const listAgentSessions = (agentId) => api.get(`/agents/${agentId}/sessions`)

export const getAgentMessages = (agentId, sessionId) =>
  api.get(`/agents/${agentId}/sessions/${sessionId}/messages`)

export const deleteAgentSession = (agentId, sessionId) =>
  api.delete(`/agents/${agentId}/sessions/${sessionId}`)

// ========== 工作流 ==========

export const listWorkflows = () => api.get('/agent-workflows')

export const createWorkflow = (data) => api.post('/agent-workflows', data)

export const updateWorkflow = (id, data) => api.put(`/agent-workflows/${id}`, data)

export const deleteWorkflow = (id) => api.delete(`/agent-workflows/${id}`)
