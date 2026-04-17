<template>
  <div class="nx-page">
    <div class="nx-page-header">
      <h2 class="nx-title">账号池</h2>
      <div class="nx-actions">
        <el-button @click="handleHealthCheckAll" :loading="checkingAll" type="warning" plain>全量健康检查</el-button>
        <el-button type="primary" @click="showAddDialog = true">新增账号</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="nx-filter-bar">
      <el-select v-model="filters.healthStatus" placeholder="健康状态" clearable @change="loadData" style="width: 140px">
        <el-option label="健康" value="healthy" />
        <el-option label="不健康" value="unhealthy" />
        <el-option label="未知" value="unknown" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索名称/端点/供应商..." clearable
        @keyup.enter="loadData" @clear="loadData" style="width: 260px" />
      <el-button @click="loadData">搜索</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="endpointUrl" label="端点" min-width="200" show-overflow-tooltip />
      <el-table-column label="API Key" width="120">
        <template #default="{ row }">
          <span class="nx-masked-key">{{ maskKey(row.apiKey) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="provider" label="供应商" width="100" />
      <el-table-column label="支持模型" min-width="180">
        <template #default="{ row }">
          <div class="nx-model-tags">
            <el-tag v-for="m in parseModels(row.supportedModels)" :key="m" size="small" class="nx-model-tag">{{ m }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="healthStatus" label="健康状态" width="100">
        <template #default="{ row }">
          <el-tag :type="healthTagType(row.healthStatus)" size="small">{{ healthLabel(row.healthStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalRequests" label="请求数" width="90" />
      <el-table-column prop="totalTokensUsed" label="Token 用量" width="110" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link @click="handleHealthCheck(row)" :loading="row._checking">检查</el-button>
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
    <el-dialog v-model="showAddDialog" :title="editingId ? '编辑账号' : '新增账号'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="账号别名" />
        </el-form-item>
        <el-form-item label="端点 URL" required>
          <el-input v-model="form.endpointUrl" placeholder="如: https://54.204.42.146" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" placeholder="API Key"/>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="form.provider" placeholder="如: litellm, openai" />
        </el-form-item>
        <el-form-item label="支持模型">
          <el-input v-model="form.supportedModelsStr" type="textarea" :rows="2"
            placeholder="每行一个模型名，如:&#10;claude-4.5-sonnet&#10;gpt-4o" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="最大 RPM">
          <el-input-number v-model="form.maxRpm" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProxyAccounts, createProxyAccount, updateProxyAccount, deleteProxyAccount,
  healthCheckAccount, healthCheckAll, enableAccount, disableAccount
} from '../../api/proxy'

const loading = ref(false)
const saving = ref(false)
const checkingAll = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({ healthStatus: '', keyword: '' })
const showAddDialog = ref(false)
const editingId = ref(null)

const form = reactive({
  name: '', endpointUrl: '', apiKey: '', provider: '', supportedModelsStr: '', weight: 1, maxRpm: 60
})

const healthTagType = (s) => ({ healthy: 'success', unhealthy: 'danger', unknown: 'info' }[s] || 'info')
const healthLabel = (s) => ({ healthy: '健康', unhealthy: '不健康', unknown: '未知' }[s] || s)

function maskKey(key) {
  if (!key) return '-'
  if (key.length <= 8) return '****'
  return key.substring(0, 4) + '****' + key.substring(key.length - 4)
}

function parseModels(models) {
  if (!models) return []
  try {
    const arr = typeof models === 'string' ? JSON.parse(models) : models
    return Array.isArray(arr) ? arr.slice(0, 5) : []
  } catch { return [] }
}

async function loadData() {
  loading.value = true
  try {
    const res = await listProxyAccounts({
      page: page.value - 1, size: pageSize.value,
      healthStatus: filters.healthStatus || undefined,
      keyword: filters.keyword || undefined
    })
    tableData.value = (res.content || []).map(r => ({ ...r, _checking: false }))
    total.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

function handleEdit(row) {
  editingId.value = row.id
  form.name = row.name || ''
  form.endpointUrl = row.endpointUrl
  form.apiKey = row.apiKey || ''
  form.provider = row.provider || ''
  form.weight = row.weight || 1
  form.maxRpm = row.maxRpm || 60
  const models = parseModels(row.supportedModels)
  form.supportedModelsStr = models.join('\n')
  showAddDialog.value = true
}

async function handleSave() {
  if (!form.endpointUrl) return ElMessage.warning('端点 URL 必填')
  saving.value = true
  try {
    const models = form.supportedModelsStr.trim().split('\n').filter(l => l.trim())
    const data = {
      name: form.name, endpointUrl: form.endpointUrl, apiKey: form.apiKey,
      provider: form.provider, supportedModels: JSON.stringify(models),
      weight: form.weight, maxRpm: form.maxRpm
    }
    if (editingId.value) {
      await updateProxyAccount(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createProxyAccount(data)
      ElMessage.success('创建成功')
    }
    showAddDialog.value = false
    editingId.value = null
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除账号 "${row.name || row.endpointUrl}"？`, '确认')
  await deleteProxyAccount(row.id)
  ElMessage.success('已删除')
  loadData()
}

async function handleHealthCheck(row) {
  row._checking = true
  try {
    const res = await healthCheckAccount(row.id)
    row.healthStatus = res.healthStatus
    row.healthMessage = res.healthMessage
    ElMessage.success(`检查完成: ${res.healthStatus}`)
  } finally {
    row._checking = false
  }
}

async function handleHealthCheckAll() {
  checkingAll.value = true
  try {
    await healthCheckAll()
    ElMessage.success('全量健康检查已完成')
    loadData()
  } finally {
    checkingAll.value = false
  }
}

async function toggleStatus(row) {
  if (row.status === 1) {
    await disableAccount(row.id)
  } else {
    await enableAccount(row.id)
  }
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.nx-page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.nx-title { font-family: var(--nx-font-mono); font-size: 18px; color: var(--nx-text-primary); }
.nx-filter-bar { display: flex; gap: 10px; margin-bottom: 16px; }
.nx-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.nx-masked-key { font-family: var(--nx-font-mono); font-size: 12px; color: var(--nx-text-muted); }
.nx-model-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.nx-model-tag { font-family: var(--nx-font-mono); font-size: 11px; }
</style>
