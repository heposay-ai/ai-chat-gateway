<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>API Key 管理</span>
          <el-button type="primary" size="small" @click="showCreateDialog = true">创建 Key</el-button>
        </div>
      </template>

      <el-table :data="keys" stripe v-if="keys.length > 0">
        <el-table-column prop="keyId" label="Key 标识" width="220">
          <template #default="{ row }">
            <span class="nx-mono">{{ row.keyId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="displayKey" label="密钥" width="140">
          <template #default="{ row }">
            <span class="nx-mono" style="color: var(--nx-text-muted)">{{ row.displayKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimit" label="频率/分" width="100">
          <template #default="{ row }">
            <span class="nx-mono">{{ row.rateLimit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最后使用" width="180">
          <template #default="{ row }">
            <span class="nx-mono" style="font-size: 12px; color: var(--nx-text-muted)">{{ row.lastUsedAt || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="warning"
              @click="toggle(row.keyId, 'disable')">禁用</el-button>
            <el-button v-else size="small" type="success"
              @click="toggle(row.keyId, 'enable')">启用</el-button>
            <el-popconfirm title="确认删除？" @confirm="del(row.keyId)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div v-else style="text-align: center; padding: 60px 20px; color: var(--nx-text-muted)">
        <p style="font-size: 48px; margin-bottom: 16px;">🔑</p>
        <p>还没有 API Key，请点击右上角「创建 Key」新建</p>
        <el-button type="primary" style="margin-top: 16px" @click="showCreateDialog = true">创建 Key</el-button>
      </div>
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建 API Key" width="400px">
      <el-form :model="form">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如：生产环境 Key" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <!-- Key 显示 -->
    <el-dialog v-model="showKeyResult" title="Key 已创建" width="500px">
      <el-alert type="warning" :closable="false" description="请立即复制保存，此 Key 只显示一次！" style="margin-bottom: 16px" />
      <el-input v-model="newKey" readonly class="nx-mono">
        <template #append>
          <el-button @click="copyKey">复制</el-button>
        </template>
      </el-input>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listKeys, createApiKey, disableKey, enableKey, removeKey } from '@/api/keys'
import { ElMessage } from 'element-plus'

const keys = ref([])
const showCreateDialog = ref(false)
const showKeyResult = ref(false)
const newKey = ref('')
const form = ref({ name: '' })
const tenantId = 1

onMounted(() => load())

async function load() {
  try { keys.value = await listKeys(tenantId) } catch (e) {}
}

async function create() {
  const result = await createApiKey(tenantId, form.value.name)
  newKey.value = result.key
  showCreateDialog.value = false
  showKeyResult.value = true
  form.value.name = ''
  load()
}

async function toggle(keyId, action) {
  if (action === 'disable') await disableKey(keyId)
  else await enableKey(keyId)
  ElMessage.success('操作成功')
  load()
}

async function del(keyId) {
  await removeKey(keyId)
  ElMessage.success('已删除')
  load()
}

function copyKey() {
  navigator.clipboard.writeText(newKey.value)
  ElMessage.success('已复制')
}
</script>
