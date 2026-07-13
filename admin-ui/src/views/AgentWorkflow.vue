<template>
  <div class="nx-workflow">
    <div class="nx-page-header">
      <span class="nx-section-title">工作流管理</span>
      <el-button type="primary" size="small" @click="openCreate">+ 创建工作流</el-button>
    </div>

    <!-- 工作流列表 -->
    <div class="nx-wf-grid" v-loading="loading">
      <div v-if="workflows.length === 0 && !loading" class="nx-empty-state">
        <div class="nx-empty-icon">_</div>
        <div>暂无工作流 // 点击上方创建</div>
      </div>

      <div v-for="wf in workflows" :key="wf.id" class="nx-wf-card nx-panel">
        <div class="nx-wf-card-content">
          <div class="nx-wf-name">{{ wf.name }}</div>
          <div class="nx-wf-desc">{{ wf.description || '暂无描述' }}</div>
          <div class="nx-wf-meta">
            <span class="nx-mono">{{ countSteps(wf.steps) }} 个步骤</span>
            <span class="nx-wf-time nx-mono">{{ formatTime(wf.createdAt) }}</span>
          </div>
        </div>
        <div class="nx-wf-actions">
          <el-button link size="small" @click="openRun(wf)" style="color: var(--nx-accent-teal)">运行</el-button>
          <el-button link size="small" @click="openEdit(wf)" style="color: var(--nx-text-secondary)">编辑</el-button>
          <el-popconfirm title="确定删除？" @confirm="handleDelete(wf.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog :title="editWf ? '编辑工作流' : '创建工作流'" v-model="showDialog" width="700px" @closed="resetForm">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="工作流名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要描述" maxlength="200" />
        </el-form-item>
        <el-form-item label="步骤">
          <div class="nx-steps-editor">
            <div v-for="(step, idx) in form.steps" :key="idx" class="nx-step-row">
              <span class="nx-step-no">{{ idx + 1 }}</span>
              <el-select v-model="step.toolName" placeholder="选择工具" size="small" style="width: 150px">
                <el-option v-for="t in toolOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="step.inputTemplate" placeholder="输入模板，如 {{userInput}}" size="small" style="flex: 1" />
              <el-input v-model="step.description" placeholder="步骤说明" size="small" style="width: 140px" />
              <el-button link type="danger" size="small" @click="form.steps.splice(idx, 1)"
                         :disabled="form.steps.length <= 1">&times;</el-button>
            </div>
            <el-button link size="small" @click="addStep" style="margin-top: 8px; color: var(--nx-accent-teal)">
              + 添加步骤
            </el-button>
            <div class="nx-step-hint">
              可用变量: <code>{{userInput}}</code>（用户输入）、<code>{{step1_output}}</code>（第1步输出）...
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          {{ editWf ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 运行对话框 -->
    <el-dialog title="运行工作流" v-model="showRunDialog" width="700px" @closed="runResult = []">
      <div class="nx-run-input">
        <span class="nx-input-prompt">></span>
        <el-input v-model="runInput" placeholder="输入内容" @keyup.enter="executeRun"
                  :disabled="isRunning" />
        <el-button type="primary" @click="executeRun" :loading="isRunning" size="small">运行</el-button>
      </div>

      <!-- 执行时间轴 -->
      <div class="nx-run-timeline" v-if="runResult.length > 0">
        <div v-for="(step, idx) in runResult" :key="idx" class="nx-timeline-item"
             :class="{ running: step.running, done: step.done, error: step.error }">
          <div class="nx-timeline-dot"></div>
          <div class="nx-timeline-content">
            <div class="nx-timeline-header">
              <span class="nx-timeline-no">Step {{ step.stepNo }}</span>
              <span class="nx-timeline-tool">{{ step.toolName }}</span>
              <span v-if="step.description" class="nx-timeline-desc">{{ step.description }}</span>
              <span v-if="step.durationMs" class="nx-timeline-dur">{{ step.durationMs }}ms</span>
            </div>
            <div v-if="step.output" class="nx-timeline-output">
              <pre class="nx-tool-pre">{{ step.output }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div v-if="runDone && !isRunning" class="nx-run-done">
        <span class="nx-mono" style="color: var(--nx-accent-teal)">// 执行完成</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listWorkflows, createWorkflow, updateWorkflow, deleteWorkflow } from '@/api/agent'

const workflows = ref([])
const loading = ref(false)
const showDialog = ref(false)
const saving = ref(false)
const editWf = ref(null)

const toolOptions = ['web_search', 'url_reader', 'knowledge_base', 'current_time', 'code_interpreter', 'llm_summarize', 'file_writer', 'kb_writer']

const form = ref({
  name: '', description: '',
  steps: [{ stepNo: 1, toolName: 'web_search', inputTemplate: '{{userInput}}', description: '' }]
})

// Run state
const showRunDialog = ref(false)
const runWfId = ref(null)
const runInput = ref('')
const runResult = ref([])
const isRunning = ref(false)
const runDone = ref(false)

function countSteps(stepsJson) {
  if (!stepsJson) return 0
  try { return JSON.parse(stepsJson).length } catch { return 0 }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

async function loadData() {
  loading.value = true
  try { workflows.value = await listWorkflows() } catch (e) {}
  loading.value = false
}

function openCreate() {
  editWf.value = null
  form.value = {
    name: '', description: '',
    steps: [{ stepNo: 1, toolName: 'web_search', inputTemplate: '{{userInput}}', description: '' }]
  }
  showDialog.value = true
}

function openEdit(wf) {
  editWf.value = wf
  let steps
  try { steps = JSON.parse(wf.steps) } catch { steps = [] }
  form.value = {
    name: wf.name, description: wf.description || '',
    steps: steps.length > 0 ? steps : [{ stepNo: 1, toolName: 'web_search', inputTemplate: '{{userInput}}', description: '' }]
  }
  showDialog.value = true
}

function resetForm() { editWf.value = null }

function addStep() {
  const no = form.value.steps.length + 1
  form.value.steps.push({ stepNo: no, toolName: 'web_search', inputTemplate: '', description: '' })
}

async function handleSave() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入名称')
  if (form.value.steps.some(s => !s.toolName)) return ElMessage.warning('请为每个步骤选择工具')
  saving.value = true
  try {
    // Re-number steps
    const steps = form.value.steps.map((s, i) => ({ ...s, stepNo: i + 1 }))
    const data = { name: form.value.name, description: form.value.description, steps: JSON.stringify(steps) }
    if (editWf.value) {
      await updateWorkflow(editWf.value.id, data)
      ElMessage.success('已更新')
    } else {
      await createWorkflow(data)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch (e) {}
  saving.value = false
}

async function handleDelete(id) {
  try { await deleteWorkflow(id); ElMessage.success('已删除'); loadData() } catch (e) {}
}

function openRun(wf) {
  runWfId.value = wf.id
  runInput.value = ''
  runResult.value = []
  runDone.value = false
  showRunDialog.value = true
}

async function executeRun() {
  if (!runInput.value.trim() || isRunning.value) return
  isRunning.value = true
  runDone.value = false
  runResult.value = []

  const apiKey = localStorage.getItem('apiKey') || ''

  try {
    const url = `/api/agent-workflows/${runWfId.value}/run?input=${encodeURIComponent(runInput.value)}`
    const resp = await fetch(url, { headers: { 'Authorization': `Bearer ${apiKey}` } })

    if (!resp.ok) {
      ElMessage.error(`请求失败: ${resp.status}`)
      isRunning.value = false; return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      let nextEvent = null
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) { nextEvent = trimmed.slice(6).trim(); continue }
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data) continue

        try {
          const parsed = JSON.parse(data)

          if (nextEvent === 'step_start') {
            runResult.value.push({
              stepNo: parsed.stepNo, toolName: parsed.toolName,
              description: parsed.description, running: true, done: false, output: '', durationMs: 0
            })
          } else if (nextEvent === 'step_end') {
            const step = runResult.value.find(s => s.stepNo === parsed.stepNo)
            if (step) {
              step.output = parsed.output
              step.durationMs = parsed.durationMs
              step.running = false
              step.done = true
            }
          } else if (parsed.type === 'done') {
            runDone.value = true
          } else if (parsed.type === 'error') {
            ElMessage.error(parsed.message || '执行失败')
          }
          nextEvent = null
        } catch (e) {}
      }
    }
    isRunning.value = false
  } catch (e) {
    isRunning.value = false
    ElMessage.error(e.message || '网络故障')
  }
}

onMounted(loadData)
</script>

<style scoped>
.nx-workflow { }
.nx-page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
}

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
  padding: 18px; display: flex; flex-direction: column;
}
.nx-wf-card-content { min-height: 60px; }
.nx-wf-name { font-size: 15px; font-weight: 600; color: var(--nx-text-primary); margin-bottom: 4px; }
.nx-wf-desc { color: var(--nx-text-secondary); font-size: 13px; margin-bottom: 12px; line-height: 1.5; }
.nx-wf-meta {
  display: flex; align-items: center; gap: 10px;
  font-size: 11px; color: var(--nx-text-muted); text-transform: uppercase; letter-spacing: 0.5px;
}
.nx-wf-time { margin-left: auto; }
.nx-wf-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--nx-border);
}

/* Steps editor */
.nx-steps-editor { width: 100%; }
.nx-step-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.nx-step-no {
  font-family: var(--nx-font-mono); font-size: 12px; color: var(--nx-accent-amber);
  width: 20px; text-align: center; flex-shrink: 0;
}
.nx-step-hint {
  font-size: 11px; color: var(--nx-text-muted); margin-top: 8px;
  font-family: var(--nx-font-mono);
}
.nx-step-hint code {
  background: var(--nx-bg-raised); padding: 1px 4px; border-radius: 2px;
  font-size: 10px; color: var(--nx-accent-teal);
}

/* Run dialog */
.nx-run-input { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.nx-input-prompt {
  font-family: var(--nx-font-mono); font-size: 16px; color: var(--nx-accent-amber);
  font-weight: 600; flex-shrink: 0;
}

/* Timeline */
.nx-run-timeline { padding-left: 16px; }
.nx-timeline-item {
  position: relative; padding-left: 20px; padding-bottom: 16px;
  border-left: 2px solid var(--nx-border);
}
.nx-timeline-item:last-child { border-left-color: transparent; }
.nx-timeline-item.running { border-left-color: var(--nx-accent-amber); }
.nx-timeline-item.done { border-left-color: var(--nx-accent-teal); }
.nx-timeline-item.error { border-left-color: var(--nx-accent-rose); }

.nx-timeline-dot {
  position: absolute; left: -6px; top: 2px; width: 10px; height: 10px;
  border-radius: 50%; background: var(--nx-border);
}
.nx-timeline-item.running .nx-timeline-dot {
  background: var(--nx-accent-amber); box-shadow: 0 0 8px var(--nx-accent-amber);
  animation: nx-pulse 1.5s infinite;
}
.nx-timeline-item.done .nx-timeline-dot { background: var(--nx-accent-teal); }
.nx-timeline-item.error .nx-timeline-dot { background: var(--nx-accent-rose); }

.nx-timeline-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.nx-timeline-no {
  font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-accent-amber);
  text-transform: uppercase; letter-spacing: 1px;
}
.nx-timeline-tool {
  font-family: var(--nx-font-mono); font-size: 12px; font-weight: 600;
  color: var(--nx-text-primary);
}
.nx-timeline-desc { font-size: 12px; color: var(--nx-text-muted); }
.nx-timeline-dur {
  font-family: var(--nx-font-mono); font-size: 10px; color: var(--nx-text-muted);
  margin-left: auto;
}

.nx-timeline-output { margin-top: 4px; }
.nx-tool-pre {
  background: var(--nx-bg-deep); border: 1px solid var(--nx-border); border-radius: 2px;
  padding: 8px 10px; font-family: var(--nx-font-mono); font-size: 11px;
  color: var(--nx-text-secondary); white-space: pre-wrap; word-break: break-all;
  max-height: 200px; overflow-y: auto; margin: 0;
}

.nx-run-done {
  text-align: center; padding: 12px 0; font-size: 12px;
}
</style>
