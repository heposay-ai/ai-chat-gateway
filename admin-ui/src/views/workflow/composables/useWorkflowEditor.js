import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getWorkflowDetail, createWorkflow, updateWorkflow,
  publishWorkflow, deleteWorkflow,
} from '@/api/workflow'

/**
 * 工作流编辑器核心逻辑
 */
export function useWorkflowEditor(workflowId) {
  const workflowName = ref('')
  const workflowDesc = ref('')
  const nodes = ref([])
  const edges = ref([])
  const selectedNode = ref(null)
  const loading = ref(false)
  const saving = ref(false)
  let nodeCounter = 0

  /** 加载已有工作流 */
  const loadWorkflow = async (id) => {
    if (!id || id === 'new') return
    loading.value = true
    try {
      const res = await getWorkflowDetail(id)
      const data = res.data || res
      const def = data.definition || data
      workflowName.value = def.name || ''
      workflowDesc.value = def.description || ''

      const nodeList = data.nodes || []
      const edgeList = data.edges || []

      nodes.value = nodeList.map(n => {
        const config = n.config ? (typeof n.config === 'string' ? JSON.parse(n.config) : n.config) : {}
        return {
          id: n.nodeKey,
          type: n.nodeType.toLowerCase(),
          position: { x: n.positionX || 0, y: n.positionY || 0 },
          data: { label: n.label, nodeType: n.nodeType, ...config },
        }
      })

      edges.value = edgeList.map(e => ({
        id: `e-${e.sourceNodeKey}-${e.targetNodeKey}`,
        source: e.sourceNodeKey,
        target: e.targetNodeKey,
        sourceHandle: e.conditionExpression || undefined,
        label: e.label || e.conditionExpression || '',
        animated: true,
        type: 'smoothstep',
      }))

      // 更新 nodeCounter
      nodeList.forEach(n => {
        const match = n.nodeKey.match(/_(\d+)$/)
        if (match) nodeCounter = Math.max(nodeCounter, parseInt(match[1]))
      })
    } catch (e) {
      ElMessage.error('加载工作流失败')
    }
    loading.value = false
  }

  /** 添加节点（拖拽放置） */
  const addNode = (nodeDef, position) => {
    nodeCounter++
    const newNode = {
      id: `${nodeDef.type}_${nodeCounter}`,
      type: nodeDef.type,
      position,
      data: {
        label: nodeDef.label,
        nodeType: nodeDef.type.toUpperCase(),
      },
    }
    nodes.value = [...nodes.value, newNode]
    return newNode
  }

  /** 添加连线 */
  const addEdge = (params) => {
    const id = `e-${params.source}-${params.target}`
    if (edges.value.some(e => e.id === id)) return
    edges.value = [...edges.value, {
      id,
      source: params.source,
      sourceHandle: params.sourceHandle,
      target: params.target,
      targetHandle: params.targetHandle,
      animated: true,
      type: 'smoothstep',
    }]
  }

  /** 删除节点 */
  const removeNode = (nodeId) => {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
    if (selectedNode.value?.id === nodeId) selectedNode.value = null
  }

  /** 删除连线 */
  const removeEdge = (edgeId) => {
    edges.value = edges.value.filter(e => e.id !== edgeId)
  }

  /** 序列化为后端格式 */
  const serialize = () => ({
    name: workflowName.value,
    description: workflowDesc.value,
    nodes: nodes.value.map(n => {
      const { label, nodeType, executionStatus, durationMs, output, ...config } = n.data
      return {
        nodeKey: n.id,
        nodeType: nodeType || n.type.toUpperCase(),
        label: label || n.type,
        positionX: Math.round(n.position.x),
        positionY: Math.round(n.position.y),
        config: JSON.stringify(config),
      }
    }),
    edges: edges.value.map(e => ({
      sourceNodeKey: e.source,
      targetNodeKey: e.target,
      conditionExpression: e.sourceHandle || null,
      label: e.label || null,
    })),
  })

  /** 保存工作流 */
  const save = async (id) => {
    if (!workflowName.value.trim()) {
      ElMessage.warning('请输入工作流名称')
      return null
    }
    saving.value = true
    try {
      const payload = serialize()
      let result
      if (id && id !== 'new') {
        result = await updateWorkflow(id, payload)
        ElMessage.success('保存成功')
      } else {
        result = await createWorkflow(payload)
        ElMessage.success('创建成功')
      }
      return result.data || result
    } catch (e) {
      ElMessage.error('保存失败')
      return null
    } finally {
      saving.value = false
    }
  }

  /** 发布工作流 */
  const publish = async (id) => {
    try {
      await publishWorkflow(id)
      ElMessage.success('已发布')
    } catch (e) {
      ElMessage.error('发布失败')
    }
  }

  return {
    workflowName, workflowDesc, nodes, edges, selectedNode,
    loading, saving,
    loadWorkflow, addNode, addEdge, removeNode, removeEdge,
    serialize, save, publish,
  }
}
