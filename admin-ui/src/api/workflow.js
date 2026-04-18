import api from './index'

// 工作流定义 CRUD
export const createWorkflow = (data) => api.post('/workflows', data)
export const getWorkflows = () => api.get('/workflows')
export const getWorkflowDetail = (id) => api.get(`/workflows/${id}`)
export const updateWorkflow = (id, data) => api.put(`/workflows/${id}`, data)
export const deleteWorkflow = (id) => api.delete(`/workflows/${id}`)
export const publishWorkflow = (id) => api.post(`/workflows/${id}/publish`)

// 模板
export const getTemplates = () => api.get('/workflows/templates')
export const cloneTemplate = (id) => api.post(`/workflows/templates/${id}/clone`)

// 执行
export const getExecutionDetail = (id) => api.get(`/workflows/executions/${id}`)
export const getExecutions = (workflowId) => api.get(`/workflows/${workflowId}/executions`)
export const getNodeExecutions = (executionId) => api.get(`/workflows/executions/${executionId}/nodes`)
export const cancelExecution = (executionId) => api.post(`/workflows/executions/${executionId}/cancel`)

// 审批
export const getPendingApprovals = () => api.get('/workflows/approvals/pending')
export const approveWorkflow = (id, data) => api.post(`/workflows/approvals/${id}/approve`, data)
export const rejectWorkflow = (id, data) => api.post(`/workflows/approvals/${id}/reject`, data)
