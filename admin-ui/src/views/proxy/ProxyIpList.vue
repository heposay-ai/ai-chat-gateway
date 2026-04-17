<template>
  <div class="nx-page">
    <div class="nx-page-header">
      <h2 class="nx-title">IP 管理</h2>
      <div class="nx-actions">
        <el-button type="primary" @click="showImportDialog = true">批量导入</el-button>
        <el-button @click="showAddDialog = true">新增 IP</el-button>
        <el-button :disabled="!selectedIds.length" type="success" plain @click="openBatchScanDialog">
          批量扫描 ({{ selectedIds.length }})
        </el-button>
        <el-button :disabled="!selectedIds.length" @click="handleBatchDelete" type="danger" plain>
          批量删除 ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="nx-filter-bar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadData" style="width: 140px">
        <el-option label="待扫描" value="pending" />
        <el-option label="扫描中" value="scanning" />
        <el-option label="活跃" value="active" />
        <el-option label="不可用" value="inactive" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索 IP/来源/标签..." clearable
        @keyup.enter="loadData" @clear="loadData" style="width: 240px" />
      <el-button @click="loadData">搜索</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" @selection-change="onSelectionChange" border stripe>
      <el-table-column type="selection" width="40" />
      <el-table-column prop="ip" label="IP 地址" width="160" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="protocol" label="协议" width="80" />
      <el-table-column prop="source" label="来源" width="120" />
      <el-table-column prop="region" label="地区" width="100" />
      <el-table-column prop="tags" label="标签" min-width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastScanAt" label="最后扫描" width="160" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="nx-pagination">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
        :total="total" :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next" @change="loadData" />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showAddDialog" :title="editingId ? '编辑 IP' : '新增 IP'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="IP 地址" required>
          <el-input v-model="form.ip" placeholder="如: 192.168.1.100" />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="协议">
          <el-select v-model="form.protocol" style="width: 120px">
            <el-option label="HTTP" value="http" />
            <el-option label="HTTPS" value="https" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="来源标识" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.region" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量扫描对话框 -->
    <el-dialog v-model="showBatchScanDialog" title="批量扫描" width="650px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        将对选中的 <b>{{ selectedIpsForScan.length }}</b> 个 IP 执行扫描
      </el-alert>
      <div class="nx-scan-targets">
        <el-tag v-for="ip in selectedIpsForScan" :key="ip.id" size="small" class="nx-scan-tag">
          {{ ip.ip }}:{{ ip.port }}
        </el-tag>
      </div>
      <el-form label-width="100px" style="margin-top: 16px">
        <el-form-item label="扫描配置" required>
          <el-select v-model="batchScanForm.scriptId" placeholder="选择扫描配置" style="width: 100%">
            <el-option v-for="s in scanScripts" :key="s.id" :label="s.name" :value="s.id">
              <span>{{ s.name }}</span>
              <span style="color: var(--nx-text-muted); font-size: 12px; margin-left: 8px">
                {{ parseScriptParam(s.defaultParams, 'timeout', 10) }}s / {{ parseScriptParam(s.defaultParams, 'workers', 10) }}w
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="batchScanForm.timeout" :min="1" :max="120" />
          <span class="nx-param-hint">秒</span>
        </el-form-item>
        <el-form-item label="并发数">
          <el-input-number v-model="batchScanForm.workers" :min="1" :max="50" />
          <span class="nx-param-hint">线程</span>
        </el-form-item>
        <el-form-item label="自定义密码">
          <el-input v-model="batchScanForm.customPasswords" type="textarea" :rows="3"
            placeholder="留空使用配置默认密码&#10;每行一个密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchScanDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBatchScan" :loading="batchScanning">开始扫描</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="showImportDialog" title="批量导入 IP" width="550px" @close="resetImportForm">
      <el-form label-width="80px">
        <el-form-item label="来源标记">
          <el-input v-model="importSource" placeholder="标记这批 IP 的来源，如: shodan, fofa" />
        </el-form-item>
        <el-form-item label="TXT 文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".txt,.csv,.text"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            drag>
            <el-icon style="font-size: 40px; color: var(--nx-text-muted)"><UploadFilled /></el-icon>
            <div style="margin-top: 8px">点击或拖拽 TXT 文件到此处</div>
            <template #tip>
              <div class="nx-upload-tip">
                每行一个，格式: <code>IP:端口</code> 或 <code>IP</code>（默认端口 4000），# 开头为注释
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="importPreview.length > 0" label="预览">
          <div class="nx-import-preview">
            <div v-for="(line, i) in importPreview" :key="i" class="nx-preview-line">
              <span class="nx-preview-idx">{{ i + 1 }}</span>
              <span>{{ line }}</span>
            </div>
            <div v-if="importFileLineCount > 5" class="nx-preview-more">... 共 {{ importFileLineCount }} 行</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBatchImport" :loading="importing" :disabled="!importFile">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { listProxyIps, createProxyIp, updateProxyIp, deleteProxyIp, batchImportIps, batchDeleteIps, listScanScripts, executeScan } from '../../api/proxy'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])

const filters = reactive({ status: '', keyword: '' })
const showAddDialog = ref(false)
const showImportDialog = ref(false)
const editingId = ref(null)
const importFile = ref(null)
const importSource = ref('')
const importPreview = ref([])
const importFileLineCount = ref(0)
const uploadRef = ref(null)

const form = reactive({
  ip: '', port: 4000, protocol: 'http', source: '', region: '', tags: '', remark: ''
})

const statusTagType = (s) => ({ pending: 'info', scanning: 'warning', active: 'success', inactive: 'danger' }[s] || 'info')
const statusLabel = (s) => ({ pending: '待扫描', scanning: '扫描中', active: '活跃', inactive: '不可用' }[s] || s)

async function loadData() {
  loading.value = true
  try {
    const res = await listProxyIps({
      page: page.value - 1, size: pageSize.value,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

function handleEdit(row) {
  editingId.value = row.id
  Object.assign(form, { ip: row.ip, port: row.port, protocol: row.protocol, source: row.source, region: row.region, tags: row.tags, remark: row.remark })
  showAddDialog.value = true
}

async function handleSave() {
  if (!form.ip || !form.port) return ElMessage.warning('IP 和端口必填')
  saving.value = true
  try {
    if (editingId.value) {
      await updateProxyIp(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createProxyIp(form)
      ElMessage.success('创建成功')
    }
    showAddDialog.value = false
    editingId.value = null
    resetForm()
    loadData()
  } finally {
    saving.value = false
  }
}

function resetForm() {
  Object.assign(form, { ip: '', port: 4000, protocol: 'http', source: '', region: '', tags: '', remark: '' })
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除 ${row.ip}:${row.port}？`, '确认')
  await deleteProxyIp(row.id)
  ElMessage.success('已删除')
  loadData()
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
  selectedRows.value = rows
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录？`, '确认')
  await batchDeleteIps(selectedIds.value)
  ElMessage.success('批量删除成功')
  loadData()
}

function onFileChange(uploadFile) {
  const raw = uploadFile.raw
  importFile.value = raw
  // 读取前 5 行预览
  const reader = new FileReader()
  reader.onload = (e) => {
    const text = e.target.result
    const lines = text.split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('#'))
    importFileLineCount.value = lines.length
    importPreview.value = lines.slice(0, 5)
  }
  reader.readAsText(raw, 'UTF-8')
}

function onFileRemove() {
  importFile.value = null
  importPreview.value = []
  importFileLineCount.value = 0
}

function resetImportForm() {
  importFile.value = null
  importSource.value = ''
  importPreview.value = []
  importFileLineCount.value = 0
  if (uploadRef.value) uploadRef.value.clearFiles()
}

async function handleBatchImport() {
  if (!importFile.value) return ElMessage.warning('请选择 TXT 文件')
  importing.value = true
  try {
    const res = await batchImportIps(importFile.value, importSource.value || 'file_import')
    ElMessage.success(`导入完成: 新增 ${res.imported || 0} 条, 跳过 ${res.skipped || 0} 条`)
    showImportDialog.value = false
    resetImportForm()
    loadData()
  } finally {
    importing.value = false
  }
}

// ============ 批量扫描 ============

const showBatchScanDialog = ref(false)
const batchScanning = ref(false)
const scanScripts = ref([])
const selectedRows = ref([])
const batchScanForm = reactive({ scriptId: null, timeout: 10, workers: 10, customPasswords: '' })

const selectedIpsForScan = computed(() => selectedRows.value)

function safeParseJson(val) {
  if (!val) return {}
  try { return typeof val === 'string' ? JSON.parse(val) : val } catch { return {} }
}

function parseScriptParam(defaultParams, key, fallback) {
  const p = safeParseJson(defaultParams)
  return p[key] ?? fallback
}

async function openBatchScanDialog() {
  if (selectedIds.value.length === 0) return ElMessage.warning('请先选择 IP')
  // 加载扫描配置列表
  try {
    scanScripts.value = await listScanScripts()
  } catch {
    scanScripts.value = []
  }
  batchScanForm.scriptId = scanScripts.value.length > 0 ? scanScripts.value[0].id : null
  batchScanForm.timeout = 10
  batchScanForm.workers = 10
  batchScanForm.customPasswords = ''
  // 如果选中的配置有默认参数，预填
  if (batchScanForm.scriptId) {
    const s = scanScripts.value.find(x => x.id === batchScanForm.scriptId)
    if (s) {
      const p = safeParseJson(s.defaultParams)
      batchScanForm.timeout = p.timeout || 10
      batchScanForm.workers = p.workers || 10
    }
  }
  showBatchScanDialog.value = true
}

async function handleBatchScan() {
  if (!batchScanForm.scriptId) return ElMessage.warning('请选择扫描配置')
  if (selectedIpsForScan.value.length === 0) return ElMessage.warning('没有选中 IP')

  batchScanning.value = true
  try {
    // 构建目标端点文本
    const targetIps = selectedIpsForScan.value
      .map(ip => `${ip.protocol || 'http'}://${ip.ip}:${ip.port}`)
      .join('\n')

    const params = {
      timeout: batchScanForm.timeout,
      workers: batchScanForm.workers
    }
    const pwdText = batchScanForm.customPasswords.trim()
    if (pwdText) {
      params.passwords = pwdText.split('\n').map(l => l.trim()).filter(l => l)
    }

    await executeScan(batchScanForm.scriptId, { targetIps, params })
    ElMessage.success('扫描任务已提交，可前往 [扫描配置] 页查看进度')
    showBatchScanDialog.value = false
  } finally {
    batchScanning.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.nx-page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.nx-title { font-family: var(--nx-font-mono); font-size: 18px; color: var(--nx-text-primary); }
.nx-filter-bar { display: flex; gap: 10px; margin-bottom: 16px; }
.nx-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.nx-param-hint { margin-left: 8px; font-size: 12px; color: var(--nx-text-muted); }
.nx-scan-targets { display: flex; flex-wrap: wrap; gap: 6px; max-height: 80px; overflow-y: auto; }
.nx-scan-tag { font-family: var(--nx-font-mono); font-size: 12px; }

.nx-upload-tip { font-size: 12px; color: var(--nx-text-muted); margin-top: 4px; }
.nx-upload-tip code { background: var(--nx-bg-raised); padding: 1px 4px; border-radius: 2px; font-family: var(--nx-font-mono); }
.nx-import-preview {
  background: var(--nx-bg-deep, #f5f5f5); border: 1px solid var(--nx-border, #e0e0e0);
  border-radius: 4px; padding: 8px 12px; font-family: var(--nx-font-mono); font-size: 12px;
  max-height: 120px; overflow-y: auto; width: 100%;
}
.nx-preview-line { display: flex; gap: 8px; line-height: 1.8; }
.nx-preview-idx { color: var(--nx-text-muted); min-width: 20px; text-align: right; }
.nx-preview-more { color: var(--nx-text-muted); margin-top: 4px; font-style: italic; }
</style>
