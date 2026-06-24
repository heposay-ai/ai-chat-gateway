import api from './index'

// ============ IP 管理 ============
export const listProxyIps = (params) => api.get('/proxy/ips', { params })
export const createProxyIp = (data) => api.post('/proxy/ips', data)
export const batchImportIps = (file, source) => {
  const formData = new FormData()
  formData.append('file', file)
  if (source) formData.append('source', source)
  return api.post('/proxy/ips/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const updateProxyIp = (id, data) => api.put(`/proxy/ips/${id}`, data)
export const deleteProxyIp = (id) => api.delete(`/proxy/ips/${id}`)
export const batchDeleteIps = (ids) => api.delete('/proxy/ips/batch', { data: { ids } })
export const listAllSimpleIps = () => api.get('/proxy/ips/all-simple')

// ============ 扫描脚本 ============
export const listScanScripts = () => api.get('/proxy/scripts')
export const createScanScript = (data) => api.post('/proxy/scripts', data)
export const updateScanScript = (id, data) => api.put(`/proxy/scripts/${id}`, data)
export const deleteScanScript = (id) => api.delete(`/proxy/scripts/${id}`)
export const executeScan = (id, data) => api.post(`/proxy/scripts/${id}/execute`, data)

// ============ 扫描任务 ============
export const listScanTasks = (params) => api.get('/proxy/scan-tasks', { params })
export const getScanTask = (id) => api.get(`/proxy/scan-tasks/${id}`)

// ============ 账号池 ============
export const listProxyAccounts = (params) => api.get('/proxy/accounts', { params })
export const createProxyAccount = (data) => api.post('/proxy/accounts', data)
export const updateProxyAccount = (id, data) => api.put(`/proxy/accounts/${id}`, data)
export const deleteProxyAccount = (id) => api.delete(`/proxy/accounts/${id}`)
export const healthCheckAccount = (id) => api.post(`/proxy/accounts/${id}/health-check`)
export const healthCheckAll = () => api.post('/proxy/accounts/health-check-all')
export const enableAccount = (id) => api.put(`/proxy/accounts/${id}/enable`)
export const disableAccount = (id) => api.put(`/proxy/accounts/${id}/disable`)

// ============ 代理池设置 ============
export const getProxySettings = () => api.get('/proxy/settings')
export const updateProxySettings = (data) => api.put('/proxy/settings', data)

// ============ 数据看板 ============
export const getDashboardOverview = () => api.get('/proxy/dashboard/overview')
export const getTokenTrend = (days) => api.get('/proxy/dashboard/token-trend', { params: { days } })
export const getCostTrend = (days) => api.get('/proxy/dashboard/cost-trend', { params: { days } })
export const getModelDistribution = (days) => api.get('/proxy/dashboard/model-distribution', { params: { days } })
export const getAccountRanking = (days, limit) => api.get('/proxy/dashboard/account-ranking', { params: { days, limit } })

// ============ 代理网关 ============
export const getGatewayHealth = () => api.get('/proxy/gateway/health')
