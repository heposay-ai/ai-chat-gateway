<template>
  <div class="nx-wf-editor">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <el-button link size="small" @click="$router.push('/workflows')" class="btn-back">
        <span class="nx-mono">&lt;-</span> 返回
      </el-button>
      <el-input v-model="workflowName" class="name-input" size="small" placeholder="工作流名称" />
      <div class="toolbar-actions">
        <el-button size="small" @click="handleSave" :loading="saving">保存</el-button>
        <el-button size="small" type="success" @click="handlePublish" plain>发布</el-button>
        <el-button size="small" type="warning" @click="showRunDialog = true" plain>运行</el-button>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="editor-main">
      <NodePalette />
      <FlowCanvas
        :nodes="nodes"
        :edges="edges"
        @update:nodes="nodes = $event"
        @update:edges="edges = $event"
        @node-select="selectedNode = $event"
        @node-deselect="selectedNode = null"
      />
      <NodeConfigPanel
        :node="selectedNode"
        @close="selectedNode = null"
        @delete="handleDeleteNode"
      />
    </div>

    <!-- 运行对话框 -->
    <el-dialog v-model="showRunDialog" title="运行工作流" width="500px">
      <el-input v-model="runInput" placeholder="输入参数..." type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="showRunDialog = false">取消</el-button>
        <el-button type="primary" @click="doRun">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NodePalette from './components/NodePalette.vue'
import FlowCanvas from './components/FlowCanvas.vue'
import NodeConfigPanel from './components/NodeConfigPanel.vue'
import { useWorkflowEditor } from './composables/useWorkflowEditor'

const route = useRoute()
const router = useRouter()
const wfId = ref(route.params.id)

const {
  workflowName, nodes, edges, selectedNode, saving,
  loadWorkflow, save, publish,
} = useWorkflowEditor(wfId.value)

const showRunDialog = ref(false)
const runInput = ref('')

onMounted(() => {
  if (wfId.value && wfId.value !== 'new') {
    loadWorkflow(wfId.value)
  }
})

async function handleSave() {
  const result = await save(wfId.value)
  if (result && wfId.value === 'new') {
    const newId = result.id || result
    wfId.value = newId
    router.replace(`/workflows/${newId}`)
  }
}

async function handlePublish() {
  if (!wfId.value || wfId.value === 'new') {
    ElMessage.warning('请先保存工作流')
    return
  }
  await publish(wfId.value)
}

function handleDeleteNode(nodeId) {
  nodes.value = nodes.value.filter(n => n.id !== nodeId)
  edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  if (selectedNode.value?.id === nodeId) selectedNode.value = null
}

function doRun() {
  showRunDialog.value = false
  if (!wfId.value || wfId.value === 'new') {
    ElMessage.warning('请先保存工作流')
    return
  }
  router.push(`/workflows/${wfId.value}/execution?input=${encodeURIComponent(runInput.value)}`)
}
</script>

<style scoped>
.nx-wf-editor {
  height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  margin: -24px;
  background: var(--nx-bg-deep);
}

.editor-toolbar {
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

.name-input {
  flex: 1;
  max-width: 300px;
}

.toolbar-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.editor-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
