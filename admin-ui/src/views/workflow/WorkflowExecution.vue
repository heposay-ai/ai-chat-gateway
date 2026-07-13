<template>
  <div class="nx-wf-execution">
    <!-- 顶部状态栏 -->
    <div class="exec-toolbar">
      <el-button link size="small" @click="$router.back()" class="btn-back">
        <span class="nx-mono">&lt;-</span> 返回
      </el-button>
      <span class="exec-title nx-mono">EXECUTION MONITOR</span>
      <span class="exec-status nx-mono" :class="status.toLowerCase()">{{ status }}</span>
      <el-button v-if="status === 'RUNNING'" size="small" type="danger" plain
                 @click="handleCancel" style="margin-left: auto">取消</el-button>
    </div>

    <!-- 主体：左画布 + 右日志 -->
    <div class="exec-main">
      <div class="exec-canvas">
        <FlowCanvas
          :nodes="canvasNodes"
          :edges="canvasEdges"
          @update:nodes="() => {}"
          @update:edges="() => {}"
          @node-select="showNodeDetail"
          @node-deselect="selectedNodeKey = null"
        />
      </div>

      <div class="exec-sidebar">
        <!-- 最终结果 -->
        <div v-if="status === 'COMPLETED'" class="result-panel">
          <div class="result-header">
            <div>
              <div class="result-status nx-mono">执行完成</div>
              <div class="result-duration nx-mono">{{ formatDuration(totalDuration) }}</div>
            </div>
            <el-button v-if="finalOutput" link size="small" @click="copyFinalOutput">复制结果</el-button>
          </div>
          <pre v-if="finalOutput" class="result-output">{{ finalOutput }}</pre>
          <div v-else class="result-empty">执行已完成，暂无文本输出</div>
        </div>

        <div v-else-if="status === 'FAILED'" class="error-panel">
          <div class="error-title nx-mono">执行失败</div>
          <div class="error-message">{{ error || '工作流执行异常，请查看执行日志' }}</div>
        </div>

        <!-- 节点详情 -->
        <div v-if="selectedNodeKey && nodeStates[selectedNodeKey]" class="node-detail-panel">
          <div class="detail-header">
            <span class="nx-mono">{{ selectedNodeKey }}</span>
            <span class="detail-status nx-mono" :class="nodeStates[selectedNodeKey].status?.toLowerCase()">
              {{ nodeStates[selectedNodeKey].status }}
            </span>
          </div>
          <div class="detail-body">
            <div v-if="nodeStates[selectedNodeKey].durationMs" class="detail-row">
              <span class="detail-label">耗时</span>
              <span class="nx-mono">{{ nodeStates[selectedNodeKey].durationMs }}ms</span>
            </div>
            <div v-if="nodeStates[selectedNodeKey].output" class="detail-row">
              <span class="detail-label">输出</span>
              <pre class="detail-output">{{ nodeStates[selectedNodeKey].output }}</pre>
            </div>
            <div v-if="nodeStates[selectedNodeKey].branch" class="detail-row">
              <span class="detail-label">分支</span>
              <span class="nx-mono">{{ nodeStates[selectedNodeKey].branch }}</span>
            </div>
          </div>
        </div>

        <!-- 审批操作 -->
        <div v-if="pendingApproval" class="approval-panel">
          <div class="approval-header nx-mono">APPROVAL REQUIRED</div>
          <div class="approval-body">
            <div class="approval-node">节点: {{ pendingApproval.nodeKey }}</div>
            <el-input v-model="approvalComment" type="textarea" :rows="2" placeholder="审批备注..." size="small" />
            <div class="approval-actions">
              <el-button type="success" size="small" @click="handleApprove">通过</el-button>
              <el-button type="danger" size="small" @click="handleReject">拒绝</el-button>
            </div>
          </div>
        </div>

        <!-- 执行日志 -->
        <ExecutionTimeline
          :logs="logs"
          :status="status"
          :totalDuration="totalDuration"
          style="flex: 1"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import FlowCanvas from './components/FlowCanvas.vue'
import ExecutionTimeline from './components/ExecutionTimeline.vue'
import { useWorkflowExecution } from './composables/useWorkflowExecution'
import { useWorkflowEditor } from './composables/useWorkflowEditor'
import { cancelExecution, approveWorkflow, rejectWorkflow } from '@/api/workflow'

const route = useRoute()
const wfId = route.params.id
const inputParam = route.query.input || ''

const { nodes, edges, loadWorkflow } = useWorkflowEditor(wfId)
const {
  executionId, status, nodeStates, totalDuration, error, logs, finalOutput, startExecution,
} = useWorkflowExecution()

const selectedNodeKey = ref(null)
const approvalComment = ref('')

// 根据执行状态渲染画布节点
const canvasNodes = computed(() =>
  nodes.value.map(n => ({
    ...n,
    data: {
      ...n.data,
      executionStatus: nodeStates[n.id]?.status || null,
      durationMs: nodeStates[n.id]?.durationMs || null,
      output: nodeStates[n.id]?.output || null,
    },
  }))
)
const canvasEdges = computed(() => edges.value)

// 查找等待审批的节点
const pendingApproval = computed(() => {
  for (const [key, state] of Object.entries(nodeStates)) {
    if (state.status === 'WAITING_APPROVAL') {
      return { nodeKey: key, approvalId: state.approvalId }
    }
  }
  return null
})

function showNodeDetail({ id }) {
  selectedNodeKey.value = id
}

function formatDuration(duration) {
  if (!duration) return ''
  return duration >= 1000 ? `${(duration / 1000).toFixed(1)} 秒` : `${duration} 毫秒`
}

async function copyFinalOutput() {
  try {
    await navigator.clipboard.writeText(finalOutput.value)
    ElMessage.success('结果已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleCancel() {
  if (!executionId.value) return
  try {
    await cancelExecution(executionId.value)
    ElMessage.success('已取消')
  } catch (e) {
    ElMessage.error('取消失败')
  }
}

async function handleApprove() {
  if (!pendingApproval.value) return
  try {
    await approveWorkflow(pendingApproval.value.approvalId, { comment: approvalComment.value })
    ElMessage.success('已通过')
    approvalComment.value = ''
  } catch (e) {
    ElMessage.error('审批失败')
  }
}

async function handleReject() {
  if (!pendingApproval.value) return
  try {
    await rejectWorkflow(pendingApproval.value.approvalId, { comment: approvalComment.value })
    ElMessage.success('已拒绝')
    approvalComment.value = ''
  } catch (e) {
    ElMessage.error('审批失败')
  }
}

onMounted(async () => {
  await loadWorkflow(wfId)
  if (inputParam || wfId) {
    startExecution(wfId, inputParam)
  }
})

watch(status, value => {
  if (value === 'COMPLETED') ElMessage.success('工作流执行完成')
  if (value === 'FAILED') ElMessage.error(error.value || '工作流执行失败')
})
</script>

<style scoped>
.nx-wf-execution {
  height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  margin: -24px;
  background: var(--nx-bg-deep);
}

.exec-toolbar {
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  border-bottom: 1px solid var(--nx-border);
  background: var(--nx-bg-surface);
  flex-shrink: 0;
}

.btn-back {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-text-muted) !important;
}

.exec-title {
  font-size: 12px;
  color: var(--nx-text-primary);
  letter-spacing: 2px;
  text-transform: uppercase;
}

.exec-status {
  font-size: 10px;
  letter-spacing: 1px;
  padding: 2px 10px;
  border-radius: 3px;
}
.exec-status.running { color: var(--nx-accent-amber); background: rgba(245,158,11,0.15); }
.exec-status.completed { color: var(--nx-accent-teal); background: rgba(45,212,191,0.15); }
.exec-status.failed { color: var(--nx-accent-rose); background: rgba(244,63,94,0.15); }
.exec-status.paused { color: var(--nx-accent-amber); background: rgba(245,158,11,0.15); }
.exec-status.idle { color: var(--nx-text-muted); background: rgba(255,255,255,0.05); }

.exec-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.exec-canvas { flex: 1; }

.exec-sidebar {
  width: 360px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--nx-border);
  flex-shrink: 0;
}

/* 最终结果 */
.result-panel,
.error-panel {
  padding: 14px 16px;
  border-bottom: 1px solid var(--nx-border);
  background: var(--nx-bg-surface);
}

.result-panel { border-left: 3px solid var(--nx-accent-teal); }
.error-panel { border-left: 3px solid var(--nx-accent-rose); }

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.result-status {
  color: var(--nx-accent-teal);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
}

.result-duration {
  color: var(--nx-text-muted);
  font-size: 10px;
  margin-top: 3px;
}

.result-output {
  max-height: 260px;
  margin: 0;
  padding: 10px;
  overflow-y: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--nx-bg-raised);
  color: var(--nx-text-primary);
  border: 1px solid var(--nx-border);
  border-radius: 4px;
  font-family: var(--nx-font-mono);
  font-size: 11px;
  line-height: 1.65;
}

.result-empty,
.error-message {
  color: var(--nx-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.error-title {
  color: var(--nx-accent-rose);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}

/* 节点详情 */
.node-detail-panel {
  border-bottom: 1px solid var(--nx-border);
  background: var(--nx-bg-surface);
}
.detail-header {
  padding: 10px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--nx-border);
  font-size: 12px;
  color: var(--nx-text-primary);
}
.detail-status { font-size: 10px; letter-spacing: 1px; }
.detail-status.running { color: var(--nx-accent-amber); }
.detail-status.completed { color: var(--nx-accent-teal); }
.detail-status.failed { color: var(--nx-accent-rose); }
.detail-body { padding: 10px 16px; }
.detail-row { margin-bottom: 8px; }
.detail-label {
  font-family: var(--nx-font-mono); font-size: 10px; color: var(--nx-text-muted);
  text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 4px;
}
.detail-output {
  font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-text-secondary);
  background: var(--nx-bg-raised); padding: 8px; border-radius: 4px;
  max-height: 120px; overflow-y: auto; white-space: pre-wrap; word-break: break-all;
  margin: 0;
}

/* 审批 */
.approval-panel {
  border-bottom: 1px solid var(--nx-border);
  background: rgba(245,158,11,0.05);
}
.approval-header {
  padding: 10px 16px;
  font-size: 11px;
  color: var(--nx-accent-amber);
  letter-spacing: 2px;
  border-bottom: 1px solid var(--nx-border);
}
.approval-body { padding: 12px 16px; }
.approval-node { font-family: var(--nx-font-mono); font-size: 12px; color: var(--nx-text-secondary); margin-bottom: 8px; }
.approval-actions { display: flex; gap: 8px; margin-top: 10px; }
</style>
