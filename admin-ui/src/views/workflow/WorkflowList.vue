<template>
  <div class="nx-workflow-list">
    <div class="nx-page-header">
      <span class="nx-section-title">工作流管理</span>
      <el-button type="primary" size="small" @click="$router.push('/workflows/new')">+ 创建工作流</el-button>
    </div>

    <!-- 模板市场 -->
    <div v-if="templates.length > 0" class="nx-template-section">
      <div class="nx-sub-title">模板市场</div>
      <div class="nx-template-grid">
        <div v-for="t in templates" :key="t.id" class="nx-template-card nx-panel"
             @click="handleClone(t)">
          <span class="nx-template-avatar">{{ categoryIcon(t.category) }}</span>
          <div class="nx-template-info">
            <div class="nx-template-name">{{ t.name }}</div>
            <div class="nx-template-desc">{{ t.description }}</div>
          </div>
          <span class="nx-template-action">复制</span>
        </div>
      </div>
    </div>

    <!-- 我的工作流 -->
    <div class="nx-sub-title" style="margin-top: 20px">我的工作流</div>
    <div class="nx-wf-grid" v-loading="loading">
      <div v-if="workflows.length === 0 && !loading" class="nx-empty-state">
        <div class="nx-empty-icon">_</div>
        <div>暂无工作流 // 创建或复制模板</div>
      </div>

      <div v-for="w in workflows" :key="w.id" class="nx-wf-card nx-panel"
           @click="$router.push(`/workflows/${w.id}`)">
        <div class="nx-wf-card-top">
          <span class="nx-wf-avatar">{{ categoryIcon(w.category) }}</span>
          <div class="nx-wf-info">
            <div class="nx-wf-name">{{ w.name }}</div>
            <div class="nx-wf-desc">{{ w.description || '暂无描述' }}</div>
          </div>
        </div>
        <div class="nx-wf-meta">
          <span class="nx-mono nx-wf-status" :class="w.status?.toLowerCase()">{{ w.status || 'DRAFT' }}</span>
          <span class="nx-wf-trigger" v-if="w.triggerType">{{ w.triggerType }}</span>
          <span class="nx-mono nx-wf-time">{{ formatTime(w.updatedAt || w.createdAt) }}</span>
        </div>
        <div class="nx-wf-actions" @click.stop>
          <el-button link size="small" @click="$router.push(`/workflows/${w.id}`)"
                     style="color: var(--nx-accent-teal)">编辑</el-button>
          <el-button link size="small" @click="handleRun(w)"
                     style="color: var(--nx-accent-amber)">运行</el-button>
          <el-popconfirm title="确定删除此工作流？" @confirm="handleDelete(w.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 运行对话框 -->
    <el-dialog v-model="showRunDialog" title="运行工作流" width="500px">
      <el-input v-model="runInput" placeholder='输入要处理的内容，如"帮我分析一下最近销售数据"' type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="showRunDialog = false">取消</el-button>
        <el-button type="primary" @click="doRun">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkflows, deleteWorkflow, getTemplates, cloneTemplate } from '@/api/workflow'

const router = useRouter()
const workflows = ref([])
const templates = ref([])
const loading = ref(false)
const showRunDialog = ref(false)
const runInput = ref('')
const runTargetId = ref(null)

const categoryIcons = { customer_service: '💬', document: '📄', data_analysis: '📊', default: '⚙️' }
function categoryIcon(cat) { return categoryIcons[cat] || categoryIcons.default }
function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

async function loadData() {
  loading.value = true
  try {
    const [wfRes, tplRes] = await Promise.all([getWorkflows(), getTemplates()])
    workflows.value = Array.isArray(wfRes) ? wfRes : (wfRes.data || [])
    templates.value = Array.isArray(tplRes) ? tplRes : (tplRes.data || [])
  } catch (e) {
    ElMessage.error('加载失败')
  }
  loading.value = false
}

async function handleClone(tpl) {
  try {
    const res = await cloneTemplate(tpl.id)
    ElMessage.success(`已从「${tpl.name}」复制`)
    const newId = res.data?.id || res.id
    if (newId) router.push(`/workflows/${newId}`)
    else loadData()
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

async function handleDelete(id) {
  try {
    await deleteWorkflow(id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function handleRun(w) {
  runTargetId.value = w.id
  runInput.value = ''
  showRunDialog.value = true
}

function doRun() {
  showRunDialog.value = false
  router.push(`/workflows/${runTargetId.value}/execution?input=${encodeURIComponent(runInput.value)}`)
}

onMounted(loadData)
</script>

<style scoped>
.nx-workflow-list { }
.nx-page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
}
.nx-sub-title {
  font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-text-muted);
  text-transform: uppercase; letter-spacing: 2px; margin-bottom: 12px;
}

/* Template */
.nx-template-section { margin-bottom: 8px; }
.nx-template-grid { display: flex; gap: 12px; flex-wrap: wrap; }
.nx-template-card {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
  cursor: pointer; transition: all 150ms; min-width: 240px;
}
.nx-template-card:hover { border-color: var(--nx-accent-amber); transform: translateY(-1px); }
.nx-template-avatar { font-size: 24px; flex-shrink: 0; }
.nx-template-info { flex: 1; min-width: 0; }
.nx-template-name { font-size: 13px; font-weight: 600; color: var(--nx-text-primary); }
.nx-template-desc {
  font-size: 11px; color: var(--nx-text-muted); margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.nx-template-action {
  font-family: var(--nx-font-mono); font-size: 10px; color: var(--nx-accent-teal);
  text-transform: uppercase; letter-spacing: 1px; flex-shrink: 0;
}

/* Workflow grid */
.nx-wf-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px;
}
.nx-empty-state {
  grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 60px 0; color: var(--nx-text-muted);
  font-family: var(--nx-font-mono); font-size: 12px; letter-spacing: 2px;
}
.nx-empty-icon { font-size: 32px; color: var(--nx-accent-amber); animation: nx-pulse 2s infinite; }

.nx-wf-card {
  padding: 18px; cursor: pointer; transition: all 150ms; display: flex; flex-direction: column;
}
.nx-wf-card:hover {
  border-color: var(--nx-accent-amber); transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(245,158,11,0.06);
}
.nx-wf-card-top { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
.nx-wf-avatar { font-size: 28px; flex-shrink: 0; margin-top: 2px; }
.nx-wf-info { flex: 1; min-width: 0; }
.nx-wf-name { font-size: 15px; font-weight: 600; color: var(--nx-text-primary); margin-bottom: 4px; }
.nx-wf-desc {
  color: var(--nx-text-secondary); font-size: 13px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.nx-wf-meta {
  display: flex; align-items: center; gap: 10px;
  font-size: 11px; color: var(--nx-text-muted); text-transform: uppercase; letter-spacing: 0.5px;
}
.nx-wf-status { padding: 1px 6px; border-radius: 3px; font-size: 10px; letter-spacing: 1px; }
.nx-wf-status.published { color: var(--nx-accent-teal); background: rgba(45,212,191,0.15); }
.nx-wf-status.draft { color: var(--nx-text-muted); background: rgba(255,255,255,0.05); }
.nx-wf-trigger { color: var(--nx-accent-amber); }
.nx-wf-time { margin-left: auto; }
.nx-wf-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--nx-border);
}
</style>
