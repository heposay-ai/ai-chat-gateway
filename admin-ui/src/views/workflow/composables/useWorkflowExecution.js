import { ref, reactive } from 'vue'
import { getExecutionDetail } from '@/api/workflow'

/**
 * 工作流执行 SSE 监听
 */
export function useWorkflowExecution() {
  const executionId = ref(null)
  const status = ref('IDLE')
  const nodeStates = reactive({})
  const totalDuration = ref(0)
  const error = ref(null)
  const logs = ref([])
  const finalOutput = ref('')

  const startExecution = async (workflowId, input) => {
    Object.keys(nodeStates).forEach(k => delete nodeStates[k])
    logs.value = []
    error.value = null
    finalOutput.value = ''
    status.value = 'RUNNING'
    const startTime = Date.now()

    try {
      const apiKey = localStorage.getItem('apiKey') || ''
      const response = await fetch(`/api/workflows/${workflowId}/execute`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-Id': '1',
          ...(apiKey ? { 'Authorization': `Bearer ${apiKey}` } : {})
        },
        body: JSON.stringify({ input }),
      })

      if (!response.ok) {
        const message = await readErrorMessage(response)
        throw new Error(message || `执行请求失败 (${response.status})`)
      }
      if (!response.body) {
        throw new Error('执行响应中没有可读取的数据流')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\r?\n\r?\n/)
        buffer = blocks.pop()
        blocks.forEach(processSseBlock)
      }
      buffer += decoder.decode()
      if (buffer.trim()) processSseBlock(buffer)

      totalDuration.value = Date.now() - startTime
      if (status.value === 'COMPLETED' && executionId.value) {
        await loadFinalOutput(executionId.value)
      } else if (status.value === 'RUNNING') {
        throw new Error('执行连接已结束，但未收到完成信号')
      }
    } catch (e) {
      error.value = e.message
      status.value = 'FAILED'
      totalDuration.value = Date.now() - startTime
    }
  }

  const loadFinalOutput = async (id) => {
    try {
      const execution = await getExecutionDetail(id)
      finalOutput.value = execution?.output || nodeStates.end?.output || ''
    } catch {
      finalOutput.value = nodeStates.end?.output || ''
    }
  }

  const readErrorMessage = async (response) => {
    const text = await response.text()
    if (!text) return ''
    try {
      const data = JSON.parse(text)
      return data?.error?.message || data?.message || text
    } catch {
      return text
    }
  }

  const processSseBlock = (block) => {
    let event = 'message'
    const dataLines = []

    for (const line of block.split(/\r?\n/)) {
      if (!line || line.startsWith(':')) continue
      const separator = line.indexOf(':')
      const field = separator >= 0 ? line.substring(0, separator) : line
      let value = separator >= 0 ? line.substring(separator + 1) : ''
      if (value.startsWith(' ')) value = value.substring(1)

      if (field === 'event') event = value.trim() || 'message'
      if (field === 'data') dataLines.push(value)
    }

    if (dataLines.length > 0) {
      handleEvent(event, dataLines.join('\n'))
    }
  }

  const handleEvent = (event, rawData) => {
    let data
    try { data = JSON.parse(rawData) } catch { data = { raw: rawData } }
    logs.value.push({ event, data, timestamp: new Date().toISOString() })

    switch (event) {
      case 'execution_start':
        executionId.value = data.executionId
        status.value = 'RUNNING'
        break
      case 'node_start':
        nodeStates[data.nodeKey] = {
          ...nodeStates[data.nodeKey],
          status: 'RUNNING', nodeType: data.nodeType, label: data.label,
          startedAt: Date.now(),
        }
        break
      case 'node_token':
        if (nodeStates[data.nodeKey]) {
          nodeStates[data.nodeKey].output = (nodeStates[data.nodeKey].output || '') + data.content
        }
        break
      case 'node_end':
        if (nodeStates[data.nodeKey]) {
          nodeStates[data.nodeKey].status = data.status
          nodeStates[data.nodeKey].durationMs = data.durationMs
          if (data.outputPreview) nodeStates[data.nodeKey].output = data.outputPreview
          if (data.branch) nodeStates[data.nodeKey].branch = data.branch
        }
        break
      case 'node_skipped':
        nodeStates[data.nodeKey] = { status: 'SKIPPED', reason: data.reason }
        break
      case 'execution_paused':
        status.value = 'PAUSED'
        if (nodeStates[data.nodeKey]) {
          nodeStates[data.nodeKey].status = 'WAITING_APPROVAL'
          nodeStates[data.nodeKey].approvalId = data.approvalId
        }
        break
      case 'execution_complete':
        status.value = data.status || 'COMPLETED'
        break
      case 'execution_error':
        status.value = 'FAILED'
        error.value = data.error
        break
    }
  }

  return {
    executionId, status, nodeStates, totalDuration, error, logs, finalOutput, startExecution,
  }
}
