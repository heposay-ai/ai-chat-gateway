<template>
  <div class="nx-page">
    <div class="nx-page-header">
      <h2 class="nx-title">扫描配置</h2>
      <el-button type="primary" @click="openCreateDialog">新增配置</el-button>
    </div>

    <el-alert type="info" :closable="false" class="nx-tip">
      扫描器已内置（Java 原生 LiteLLM Scanner），无需外部 Python 环境。
      创建扫描配置后，选择目标 IP 即可一键执行。
    </el-alert>

    <!-- 配置列表 -->
    <el-table :data="scripts" v-loading="loading" border stripe style="margin-top: 16px">
      <el-table-column prop="name" label="配置名称" width="160" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="超时/并发" width="120">
        <template #default="{ row }">
          <span class="nx-mono">{{ parseParam(row.defaultParams, 'timeout', 10) }}s / {{ parseParam(row.defaultParams, 'workers', 10) }}w</span>
        </template>
      </el-table-column>
      <el-table-column label="自定义密码" width="100">
        <template #default="{ row }">
          <el-tag v-if="hasCustomPasswords(row.defaultParams)" size="small" type="warning">自定义</el-tag>
          <el-tag v-else size="small" type="info">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link @click="handleEdit(row)">编辑</el-button>
          <el-button link type="success" @click="openExecuteDialog(row)">执行扫描</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 扫描任务历史 -->
    <div class="nx-section">
      <div class="nx-section-header">
        <h3 class="nx-subtitle">扫描任务历史</h3>
        <el-button size="small" @click="loadTasks">刷新</el-button>
      </div>
      <el-table :data="tasks" v-loading="tasksLoading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标端点" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="nx-mono">{{ summarizeTargets(row.targetIps) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="扫描结果" width="160">
          <template #default="{ row }">
            <template v-if="row.resultSummary">
              <span class="nx-result-found" v-if="parseSummary(row.resultSummary).totalFound > 0">
                发现 {{ parseSummary(row.resultSummary).totalFound }} 个弱密码,
                入库 {{ parseSummary(row.resultSummary).imported }}
              </span>
              <span v-else class="nx-result-safe">安全</span>
            </template>
            <span v-else class="nx-mono nx-text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="160" />
        <el-table-column prop="completedAt" label="完成时间" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link @click="viewTaskLog(row)">查看日志</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="nx-pagination">
        <el-pagination v-model:current-page="taskPage" :total="taskTotal"
          :page-size="10" layout="total, prev, pager, next" @change="loadTasks" />
      </div>
    </div>

    <!-- 新增/编辑配置对话框 -->
    <el-dialog v-model="showScriptDialog" :title="editingId ? '编辑扫描配置' : '新增扫描配置'" width="600px"
      @close="resetScriptForm">
      <el-form :model="scriptForm" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="scriptForm.name" placeholder="如: 默认弱密码扫描" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="scriptForm.description" type="textarea" :rows="2"
            placeholder="扫描配置的用途说明" />
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="scriptForm.params.timeout" :min="1" :max="120" />
          <span class="nx-param-hint">秒 / 每个请求</span>
        </el-form-item>
        <el-form-item label="并发数">
          <el-input-number v-model="scriptForm.params.workers" :min="1" :max="50" />
          <span class="nx-param-hint">线程</span>
        </el-form-item>
        <el-form-item label="密码列表">
          <el-input v-model="scriptForm.params.customPasswords" type="textarea" :rows="5"
            placeholder="每行一个密码，留空使用内置默认密码列表&#10;&#10;内置列表包含: sk-1234, sk-12345, sk-default, sk-test, admin, password 等常见弱密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showScriptDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveScript" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行扫描对话框 -->
    <el-dialog v-model="showExecDialog" title="执行扫描" width="750px">
      <el-form label-width="100px">
        <el-form-item label="扫描配置">
          <el-tag type="primary">{{ execConfigName }}</el-tag>
        </el-form-item>
        <el-form-item label="目标端点" required>
          <el-tabs v-model="execForm.ipSource" class="nx-ip-tabs">
            <el-tab-pane label="从 IP 池选择" name="pool">
              <div class="nx-ip-pool-toolbar">
                <el-input v-model="ipPoolSearch" placeholder="搜索 IP / 来源 / 地区..." clearable
                  style="width: 240px" size="small" />
                <el-tag size="small" type="info">已选 {{ execForm.selectedIpIds.length }} 个</el-tag>
              </div>
              <el-table :data="filteredIpPool" height="260" size="small" border
                @selection-change="onIpPoolSelectionChange" ref="ipPoolTableRef">
                <el-table-column type="selection" width="40" />
                <el-table-column prop="ip" label="IP" width="150" />
                <el-table-column prop="port" label="端口" width="70" />
                <el-table-column prop="protocol" label="协议" width="70" />
                <el-table-column prop="source" label="来源" width="100" show-overflow-tooltip />
                <el-table-column prop="region" label="地区" width="80" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag :type="ipStatusType(row.status)" size="small">{{ ipStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="手动输入" name="manual">
              <el-input v-model="execForm.targetIps" type="textarea" :rows="8"
                placeholder="每行一个端点，格式: IP:端口 或完整 URL&#10;&#10;示例:&#10;192.168.1.100:4000&#10;http://10.0.0.1:8080&#10;https://api.example.com" />
            </el-tab-pane>
          </el-tabs>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="execForm.timeout" :min="1" :max="120" />
          <span class="nx-param-hint">秒</span>
        </el-form-item>
        <el-form-item label="并发数">
          <el-input-number v-model="execForm.workers" :min="1" :max="50" />
          <span class="nx-param-hint">线程</span>
        </el-form-item>
        <el-form-item label="自定义密码">
          <el-input v-model="execForm.customPasswords" type="textarea" :rows="4"
            placeholder="留空则使用配置中的密码列表（或内置默认列表）&#10;每行一个密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExecDialog = false">取消</el-button>
        <el-button type="primary" @click="handleExecute" :loading="executing">开始扫描</el-button>
      </template>
    </el-dialog>

    <!-- 任务日志对话框 -->
    <el-dialog v-model="showLogDialog" title="扫描日志" width="800px" top="5vh">
      <div v-if="currentSummary" class="nx-log-summary">
        <span>端点: {{ currentSummary.totalEndpoints || '-' }}</span>
        <span>发现弱密码: <b :class="{ 'nx-result-found': currentSummary.totalFound > 0 }">{{ currentSummary.totalFound || 0 }}</b></span>
        <span>入库账号: <b>{{ currentSummary.imported || 0 }}</b></span>
      </div>
      <pre class="nx-log-output">{{ currentLog }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listScanScripts, createScanScript, updateScanScript, deleteScanScript, executeScan, listScanTasks, getScanTask, listAllSimpleIps } from '../../api/proxy'

const loading = ref(false)
const saving = ref(false)
const executing = ref(false)
const tasksLoading = ref(false)
const scripts = ref([])
const tasks = ref([])
const taskPage = ref(1)
const taskTotal = ref(0)

const showScriptDialog = ref(false)
const showExecDialog = ref(false)
const showLogDialog = ref(false)
const editingId = ref(null)
const currentLog = ref('')
const currentSummary = ref(null)
const execScriptId = ref(null)
const execConfigName = ref('')

const scriptForm = reactive({
  name: '', description: '',
  params: { timeout: 10, workers: 10, customPasswords: '' }
})

const execForm = reactive({ targetIps: '', timeout: 10, workers: 10, customPasswords: '', ipSource: 'pool', selectedIpIds: [] })

// IP 池选择器
const ipPool = ref([])
const ipPoolSearch = ref('')
const ipPoolTableRef = ref(null)

const filteredIpPool = computed(() => {
  const kw = ipPoolSearch.value.trim().toLowerCase()
  if (!kw) return ipPool.value
  return ipPool.value.filter(ip =>
    ip.ip.toLowerCase().includes(kw) ||
    (ip.source || '').toLowerCase().includes(kw) ||
    (ip.region || '').toLowerCase().includes(kw)
  )
})

const ipStatusType = (s) => ({ pending: 'info', scanning: 'warning', active: 'success', inactive: 'danger' }[s] || 'info')
const ipStatusLabel = (s) => ({ pending: '待扫描', scanning: '扫描中', active: '活跃', inactive: '不可用' }[s] || s)

async function loadIpPool() {
  try {
    ipPool.value = await listAllSimpleIps()
  } catch {
    ipPool.value = []
  }
}

function onIpPoolSelectionChange(rows) {
  execForm.selectedIpIds = rows.map(r => r.id)
}

function buildTargetIpsFromPool() {
  const selected = ipPool.value.filter(ip => execForm.selectedIpIds.includes(ip.id))
  return selected.map(ip => `${ip.protocol || 'http'}://${ip.ip}:${ip.port}`).join('\n')
}

const taskStatusType = (s) => ({ pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }[s] || 'info')
const taskStatusLabel = (s) => ({ pending: '等待中', running: '执行中', completed: '已完成', failed: '失败' }[s] || s)

function safeParseJson(val) {
  if (!val) return {}
  try { return typeof val === 'string' ? JSON.parse(val) : val } catch { return {} }
}

function parseParam(defaultParams, key, fallback) {
  const p = safeParseJson(defaultParams)
  return p[key] ?? fallback
}

function hasCustomPasswords(defaultParams) {
  const p = safeParseJson(defaultParams)
  return p.passwords && Array.isArray(p.passwords) && p.passwords.length > 0
}

function summarizeTargets(targetIps) {
  if (!targetIps) return '-'
  const lines = targetIps.trim().split('\n').filter(l => l.trim())
  if (lines.length <= 2) return lines.join(', ')
  return `${lines[0]}, ... 共 ${lines.length} 个`
}

function parseSummary(raw) {
  return safeParseJson(raw)
}

// ============ 配置 CRUD ============

async function loadScripts() {
  loading.value = true
  try {
    scripts.value = await listScanScripts()
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingId.value = null
  resetScriptForm()
  showScriptDialog.value = true
}

function resetScriptForm() {
  scriptForm.name = ''
  scriptForm.description = ''
  scriptForm.params.timeout = 10
  scriptForm.params.workers = 10
  scriptForm.params.customPasswords = ''
}

function handleEdit(row) {
  editingId.value = row.id
  scriptForm.name = row.name
  scriptForm.description = row.description || ''
  const p = safeParseJson(row.defaultParams)
  scriptForm.params.timeout = p.timeout || 10
  scriptForm.params.workers = p.workers || 10
  scriptForm.params.customPasswords = Array.isArray(p.passwords) ? p.passwords.join('\n') : ''
  showScriptDialog.value = true
}

async function handleSaveScript() {
  if (!scriptForm.name) return ElMessage.warning('配置名称必填')
  saving.value = true
  try {
    // 构建 defaultParams JSON
    const paramObj = {
      timeout: scriptForm.params.timeout,
      workers: scriptForm.params.workers
    }
    // 自定义密码: textarea 每行一个 -> JSON 数组
    const pwdText = scriptForm.params.customPasswords.trim()
    if (pwdText) {
      paramObj.passwords = pwdText.split('\n').map(l => l.trim()).filter(l => l)
    }

    const data = {
      name: scriptForm.name,
      description: scriptForm.description,
      scriptType: 'java',
      scriptPath: 'builtin:litellm-scanner',
      defaultParams: JSON.stringify(paramObj)
    }

    if (editingId.value) {
      await updateScanScript(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createScanScript(data)
      ElMessage.success('创建成功')
    }
    showScriptDialog.value = false
    editingId.value = null
    loadScripts()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除配置 "${row.name}"？`, '确认')
  await deleteScanScript(row.id)
  ElMessage.success('已删除')
  loadScripts()
}

// ============ 执行扫描 ============

function openExecuteDialog(row) {
  execScriptId.value = row.id
  execConfigName.value = row.name
  const p = safeParseJson(row.defaultParams)
  execForm.timeout = p.timeout || 10
  execForm.workers = p.workers || 10
  execForm.targetIps = ''
  execForm.ipSource = 'pool'
  execForm.selectedIpIds = []
  ipPoolSearch.value = ''
  // 预填配置中的自定义密码
  execForm.customPasswords = Array.isArray(p.passwords) ? p.passwords.join('\n') : ''
  showExecDialog.value = true
  loadIpPool()
}

async function handleExecute() {
  // 根据来源构建目标 IP 文本
  let targetIps = ''
  if (execForm.ipSource === 'pool') {
    if (execForm.selectedIpIds.length === 0) return ElMessage.warning('请从 IP 池中选择至少一个端点')
    targetIps = buildTargetIpsFromPool()
  } else {
    targetIps = execForm.targetIps.trim()
    if (!targetIps) return ElMessage.warning('请输入目标端点')
  }

  executing.value = true
  try {
    const params = {
      timeout: execForm.timeout,
      workers: execForm.workers
    }
    const pwdText = execForm.customPasswords.trim()
    if (pwdText) {
      params.passwords = pwdText.split('\n').map(l => l.trim()).filter(l => l)
    }

    await executeScan(execScriptId.value, { targetIps, params })
    ElMessage.success('扫描任务已提交，可在下方查看进度')
    showExecDialog.value = false
    loadTasks()
  } finally {
    executing.value = false
  }
}

// ============ 任务历史 ============

async function loadTasks() {
  tasksLoading.value = true
  try {
    const res = await listScanTasks({ page: taskPage.value - 1, size: 10 })
    tasks.value = res.content || []
    taskTotal.value = res.totalElements || 0
  } finally {
    tasksLoading.value = false
  }
}

async function viewTaskLog(row) {
  const detail = await getScanTask(row.id)
  currentLog.value = detail.logOutput || '暂无日志'
  currentSummary.value = safeParseJson(detail.resultSummary)
  showLogDialog.value = true
}

onMounted(() => { loadScripts(); loadTasks() })
</script>

<style scoped>
.nx-page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.nx-title { font-family: var(--nx-font-mono); font-size: 18px; color: var(--nx-text-primary); }
.nx-subtitle { font-family: var(--nx-font-mono); font-size: 15px; color: var(--nx-text-primary); margin: 0; }
.nx-section { margin-top: 32px; }
.nx-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.nx-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.nx-tip { margin-bottom: 0; }

.nx-mono { font-family: var(--nx-font-mono); font-size: 12px; }
.nx-text-muted { color: var(--nx-text-muted); }
.nx-param-hint { margin-left: 8px; font-size: 12px; color: var(--nx-text-muted); }

.nx-result-found { color: var(--nx-accent-rose, #e74c3c); font-weight: 600; }
.nx-result-safe { color: var(--nx-accent-teal, #2ecc71); font-family: var(--nx-font-mono); font-size: 12px; }

.nx-log-summary {
  display: flex; gap: 24px; padding: 12px 16px; margin-bottom: 12px;
  background: var(--nx-bg-raised); border: 1px solid var(--nx-border); border-radius: 4px;
  font-family: var(--nx-font-mono); font-size: 13px; color: var(--nx-text-secondary);
}
.nx-log-summary b { color: var(--nx-text-primary); }

.nx-log-output {
  background: var(--nx-bg-deep); color: var(--nx-text-primary); padding: 16px; border-radius: 4px;
  font-family: var(--nx-font-mono); font-size: 12px; max-height: 500px; overflow: auto; white-space: pre-wrap;
  border: 1px solid var(--nx-border); margin: 0;
}

.nx-ip-tabs { width: 100%; }
.nx-ip-pool-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
</style>
