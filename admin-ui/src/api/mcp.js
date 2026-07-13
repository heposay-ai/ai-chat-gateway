import api from './index'

export function listMcpPresets() {
  return api.get('/mcp/presets')
}

export function listMcpTools(serverUrl) {
  return api.post('/mcp/tools', { serverUrl })
}

export function callMcpTool(serverUrl, toolName, arguments_) {
  const apiKey = localStorage.getItem('apiKey') || ''
  return fetch('/api/mcp/tools/call', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`
    },
    body: JSON.stringify({ serverUrl, toolName, arguments: arguments_ })
  }).then(resp => {
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    return resp.body.getReader()
  })
}
