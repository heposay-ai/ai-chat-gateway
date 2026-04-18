import { ref, reactive } from 'vue'

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

  const startExecution = async (workflowId, input) => {
    Object.keys(nodeStates).forEach(k => delete nodeStates[k])
    logs.value = []
    error.value = null
    status.value = 'RUNNING'
    const startTime = Date.now()

    try {
      const response = await fetch(`/api/workflows/${workflowId}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': '1' },
        body: JSON.stringify({ input }),
      })

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()

        let currentEvent = null
        for (const line of lines) {
          if (line.startsWith('event: ')) {
            currentEvent = line.substring(7).trim()
          } else if (line.startsWith('data: ')) {
            const data = line.substring(6).trim()
            handleEvent(currentEvent || 'message', data)
            currentEvent = null
          }
        }
      }
      totalDuration.value = Date.now() - startTime
    } catch (e) {
      error.value = e.message
      status.value = 'FAILED'
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

  return { executionId, status, nodeStates, totalDuration, error, logs, startExecution }
}
