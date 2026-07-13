<template>
  <div class="nx-agent-list">
    <div class="nx-page-header">
      <span class="nx-section-title">Agent 管理</span>
      <el-button type="primary" size="small" @click="openCreate">+ 创建 Agent</el-button>
    </div>

    <!-- 预设模板区 -->
    <div v-if="templates.length > 0" class="nx-template-section">
      <div class="nx-sub-title">预设模板</div>
      <div class="nx-template-grid">
        <div v-for="t in templates" :key="t.id" class="nx-template-card nx-panel"
             @click="handleClone(t)">
          <span class="nx-template-avatar">{{ t.avatar || '🤖' }}</span>
          <div class="nx-template-info">
            <div class="nx-template-name">{{ t.name }}</div>
            <div class="nx-template-desc">{{ t.description }}</div>
          </div>
          <span class="nx-template-action">复制</span>
        </div>
      </div>
    </div>

    <!-- Agent 列表 -->
    <div class="nx-sub-title" style="margin-top: 20px">我的 Agent</div>
    <div class="nx-agent-grid" v-loading="loading">
      <div v-if="agents.length === 0 && !loading" class="nx-empty-state">
        <div class="nx-empty-icon">_</div>
        <div>暂无 Agent // 创建或复制模板</div>
      </div>

      <div v-for="a in agents" :key="a.id" class="nx-agent-card nx-panel"
           @click="$router.push(`/agents/${a.id}/chat`)">
        <div class="nx-agent-card-top">
          <span class="nx-agent-avatar">{{ a.avatar || '🤖' }}</span>
          <div class="nx-agent-info">
            <div class="nx-agent-name">{{ a.name }}</div>
            <div class="nx-agent-desc">{{ a.description || '暂无描述' }}</div>
          </div>
        </div>
        <div class="nx-agent-meta">
          <span class="nx-mono">{{ a.modelId }}</span>
          <span class="nx-agent-tools">
            {{ parseTools(a.toolsConfig).length }} 个工具
          </span>
          <span class="nx-agent-time nx-mono">{{ formatTime(a.createdAt) }}</span>
        </div>
        <div class="nx-agent-actions" @click.stop>
          <el-button link size="small" @click="openEdit(a)" style="color: var(--nx-text-secondary)">编辑</el-button>
          <el-button link size="small" @click="$router.push(`/agents/${a.id}/chat`)"
                     style="color: var(--nx-accent-teal)">对话</el-button>
          <el-popconfirm title="确定删除此 Agent？" @confirm="handleDelete(a.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog :title="editAgent ? '编辑 Agent' : '创建 Agent'" v-model="showDialog" width="600px" @closed="resetForm">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="Agent 名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="头像">
          <el-input v-model="form.avatar" placeholder="输入 emoji，如 🌐 📚 🤖" style="width: 120px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要描述 Agent 用途" maxlength="200" />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.modelId" style="width: 100%">
            <el-option label="DeepSeek Chat" value="deepseek-chat" />
            <el-option label="通义千问 Plus" value="qwen-plus" />
            <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
            <el-option label="Claude Sonnet" value="claude-sonnet" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4"
                    placeholder="你是一个智能助手..." />
        </el-form-item>
        <el-form-item label="工具">
          <el-checkbox-group v-model="form.selectedTools">
            <el-checkbox v-for="tool in availableTools" :key="tool.name"
                         :label="tool.name">{{ tool.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          {{ editAgent ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAgents, listTemplates, listTools, createAgent, updateAgent, deleteAgent, cloneTemplate } from '@/api/agent'

const agents = ref([])
const templates = ref([])
const availableTools = ref([])
const loading = ref(false)
const showDialog = ref(false)
const saving = ref(false)
const editAgent = ref(null)

const form = ref({
  name: '', avatar: '', description: '', modelId: 'deepseek-chat',
  systemPrompt: '你是一个智能助手，请根据用户的问题给出有帮助的回答。',
  selectedTools: []
})

function parseTools(config) {
  if (!config) return []
  try { return JSON.parse(config) } catch { return [] }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

async function loadData() {
  loading.value = true
  try {
    const [agentList, tplList, toolList] = await Promise.all([
      listAgents(), listTemplates(), listTools()
    ])
    agents.value = agentList
    templates.value = tplList
    availableTools.value = toolList
  } catch (e) {}
  loading.value = false
}

function openCreate() {
  editAgent.value = null
  form.value = {
    name: '', avatar: '', description: '', modelId: 'deepseek-chat',
    systemPrompt: '你是一个智能助手，请根据用户的问题给出有帮助的回答。',
    selectedTools: []
  }
  showDialog.value = true
}

function openEdit(a) {
  editAgent.value = a
  form.value = {
    name: a.name, avatar: a.avatar || '', description: a.description || '',
    modelId: a.modelId, systemPrompt: a.systemPrompt,
    selectedTools: parseTools(a.toolsConfig)
  }
  showDialog.value = true
}

function resetForm() { editAgent.value = null }

async function handleSave() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入名称')
  if (!form.value.systemPrompt.trim()) return ElMessage.warning('请输入系统提示词')
  saving.value = true
  try {
    const data = {
      name: form.value.name,
      avatar: form.value.avatar,
      description: form.value.description,
      modelId: form.value.modelId,
      systemPrompt: form.value.systemPrompt,
      toolsConfig: JSON.stringify(form.value.selectedTools)
    }
    if (editAgent.value) {
      await updateAgent(editAgent.value.id, data)
      ElMessage.success('已更新')
    } else {
      await createAgent(data)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch (e) {}
  saving.value = false
}

async function handleDelete(id) {
  try { await deleteAgent(id); ElMessage.success('已删除'); loadData() } catch (e) {}
}

async function handleClone(tpl) {
  try {
    await cloneTemplate(tpl.id)
    ElMessage.success(`已从「${tpl.name}」复制`)
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style scoped>
.nx-agent-list { }
.nx-page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
}
.nx-sub-title {
  font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-text-muted);
  text-transform: uppercase; letter-spacing: 2px; margin-bottom: 12px;
}

/* Template section */
.nx-template-section { margin-bottom: 8px; }
.nx-template-grid { display: flex; gap: 12px; flex-wrap: wrap; }
.nx-template-card {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
  cursor: pointer; transition: all 150ms; min-width: 240px;
}
.nx-template-card:hover {
  border-color: var(--nx-accent-amber);
  transform: translateY(-1px);
}
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

/* Agent grid */
.nx-agent-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px;
}
.nx-empty-state {
  grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 60px 0; color: var(--nx-text-muted);
  font-family: var(--nx-font-mono); font-size: 12px; letter-spacing: 2px;
}
.nx-empty-icon { font-size: 32px; color: var(--nx-accent-amber); animation: nx-pulse 2s infinite; }

.nx-agent-card {
  padding: 18px; cursor: pointer; transition: all 150ms;
  display: flex; flex-direction: column;
}
.nx-agent-card:hover {
  border-color: var(--nx-accent-amber);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(245, 158, 11, 0.06);
}
.nx-agent-card-top { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
.nx-agent-avatar { font-size: 28px; flex-shrink: 0; margin-top: 2px; }
.nx-agent-info { flex: 1; min-width: 0; }
.nx-agent-name { font-size: 15px; font-weight: 600; color: var(--nx-text-primary); margin-bottom: 4px; }
.nx-agent-desc {
  color: var(--nx-text-secondary); font-size: 13px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.nx-agent-meta {
  display: flex; align-items: center; gap: 10px;
  font-size: 11px; color: var(--nx-text-muted); text-transform: uppercase; letter-spacing: 0.5px;
}
.nx-agent-tools { color: var(--nx-accent-teal); }
.nx-agent-time { margin-left: auto; }
.nx-agent-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--nx-border);
}
</style>
