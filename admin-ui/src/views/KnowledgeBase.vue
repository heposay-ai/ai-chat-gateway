<template>
  <div class="nx-kb-legacy">
    <!-- 文档管理区 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>文档管理</span>
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pdf,.docx,.txt,.md,.csv">
            <el-button type="primary" size="small" :loading="uploading">上传</el-button>
          </el-upload>
        </div>
      </template>

      <el-table :data="documents" v-loading="loadingDocs" empty-text="暂无文档" size="small">
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }"><span class="nx-mono">{{ formatSize(row.fileSize) }}</span></template>
        </el-table-column>
        <el-table-column label="字符数" width="90">
          <template #default="{ row }"><span class="nx-mono">{{ row.charCount }}</span></template>
        </el-table-column>
        <el-table-column label="分块数" width="90">
          <template #default="{ row }"><span class="nx-mono">{{ row.chunkCount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="nx-mono" style="font-size: 12px; color: var(--nx-text-muted)">
              {{ row.createdAt?.replace('T', ' ').substring(0, 19) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- RAG 对话区 -->
    <el-card class="nx-rag-card">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>RAG 问答</span>
          <el-select v-model="model" size="small" style="width: 160px">
            <el-option label="DeepSeek Chat" value="deepseek-chat" />
            <el-option label="通义千问" value="qwen-plus" />
            <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
            <el-option label="Claude Sonnet" value="claude-sonnet" />
          </el-select>
        </div>
      </template>

      <div class="nx-log-area nx-rag-legacy-log" ref="messagesRef">
        <div v-if="messages.length === 0" class="nx-empty" style="height: 100%">
          <div class="nx-empty-icon" style="font-size: 24px">_</div>
          <div style="font-size: 11px">上传文档 // 开始提问</div>
        </div>
        <div v-for="(msg, i) in messages" :key="i" class="nx-log-entry">
          <div class="nx-log-header">
            <span :class="['nx-log-role', msg.role]">
              {{ msg.role === 'user' ? '> 用户' : '  系统' }}
            </span>
            <span class="nx-log-time">{{ msg.time || '' }}</span>
            <span class="nx-log-rule"></span>
          </div>
          <div class="nx-log-body" :class="{ 'user-text': msg.role === 'user' }">
            <template v-if="msg.role === 'user'">{{ msg.content }}</template>
            <template v-else>
              <div class="nx-markdown" v-html="renderMd(msg.content)"></div>
              <span v-if="i === messages.length - 1 && isStreaming" class="nx-log-cursor"></span>
            </template>
          </div>
          <div v-if="msg.sources && msg.sources.length" class="nx-sources-box">
            <div class="nx-sources-title">引用来源</div>
            <div v-for="(s, si) in msg.sources" :key="si" class="nx-source-item">
              <span>{{ s.fileName }}<span v-if="s.page"> · 第 {{ s.page }} 页</span></span>
              <span class="nx-source-score">{{ (s.score * 100).toFixed(0) }}%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="nx-input-bar">
        <span class="nx-input-prompt">></span>
        <el-input v-model="inputMsg" type="textarea" placeholder="输入问题..."
          :autosize="{ minRows: 2, maxRows: 6 }"
          @keydown.enter.exact.prevent="send" :disabled="isStreaming" />
        <el-button type="primary" @click="send" :loading="isStreaming">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocuments, uploadDocument, deleteDocument } from '@/api/rag'
import { renderMarkdown } from '@/utils/markdown'

const documents = ref([])
const loadingDocs = ref(false)
const uploading = ref(false)

async function loadDocuments() {
  loadingDocs.value = true
  try { documents.value = await getDocuments() } catch (e) {}
  loadingDocs.value = false
}

async function handleUpload(file) {
  uploading.value = true
  try {
    const doc = await uploadDocument(file)
    if (doc?.duplicate) {
      ElMessage.info('文档已存在，已跳过重复索引')
    } else {
      ElMessage.success('上传成功，正在处理...')
    }
    pollDocuments()
  } catch (e) {}
  uploading.value = false
  return false
}

function pollDocuments() {
  loadDocuments()
  const timer = setInterval(async () => {
    await loadDocuments()
    const processing = documents.value.some(d => d.status === 0 || d.status === 1)
    if (!processing) clearInterval(timer)
  }, 3000)
  setTimeout(() => clearInterval(timer), 120000)
}

async function handleDelete(id) {
  try { await deleteDocument(id); ElMessage.success('已删除'); loadDocuments() } catch (e) {}
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

function statusText(s) {
  const map = { 0: '索引中', 1: '已解析', 2: '就绪', '-1': '失败' }
  return map[s] || '未知'
}

function statusType(s) {
  const map = { 0: 'warning', 1: 'warning', 2: 'success', '-1': 'danger' }
  return map[s] || 'info'
}

// Chat
const messages = ref([])
const inputMsg = ref('')
const model = ref('deepseek-chat')
const isStreaming = ref(false)
const messagesRef = ref(null)

function now() { return new Date().toLocaleTimeString('en-GB', { hour12: false }) }

function normalizeMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```([A-Za-z0-9_-]+)[ \t]+/g, '```$1\n')
    .replace(/[ \t]+```/g, '\n```')
}

function renderMd(text) { return renderMarkdown(normalizeMarkdown(text)) }

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return
  const question = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: question, time: now() })
  messages.value.push({ role: 'assistant', content: '', sources: [], time: now() })
  scrollToBottom()

  isStreaming.value = true
  const apiKey = localStorage.getItem('apiKey') || ''

  try {
    const url = `/api/rag/stream?q=${encodeURIComponent(question)}&model=${model.value}`
    const resp = await fetch(url, { headers: { 'Authorization': `Bearer ${apiKey}` } })

    if (!resp.ok) {
      const last = messages.value[messages.value.length - 1]
      last.content = resp.status === 401 ? '[错误] 未授权' : `[错误] ${resp.status}`
      isStreaming.value = false; return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let nextIsSources = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) {
          nextIsSources = trimmed.replace('event:', '').trim() === 'sources'
          continue
        }
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          if (nextIsSources && Array.isArray(parsed)) {
            last.sources = parsed; nextIsSources = false
          } else if (parsed.content) last.content += parsed.content
          scrollToBottom()
        } catch (e) {}
      }
    }
    isStreaming.value = false
  } catch (e) {
    isStreaming.value = false
    const last = messages.value[messages.value.length - 1]
    if (!last.content) last.content = '[错误] 网络故障'
  }
}

onMounted(() => { loadDocuments() })
</script>

<style scoped>
.nx-kb-legacy {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 24px);
  min-height: 0;
}
.nx-rag-card {
  flex: 1;
  min-height: 0;
}
.nx-rag-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: calc(100% - 49px);
}
.nx-rag-legacy-log {
  flex: 1;
  min-height: 340px;
  margin-bottom: 14px;
  padding: 22px 28px;
  scroll-behavior: smooth;
}
.nx-empty { display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: var(--nx-text-muted); font-family: var(--nx-font-mono); font-size: 12px;
  text-transform: uppercase; letter-spacing: 2px; gap: 8px; }
.nx-empty-icon { color: var(--nx-accent-amber); animation: nx-pulse 2s infinite; }
.nx-kb-legacy :deep(.nx-log-entry) { max-width: 1120px; margin: 0 auto 24px; }
.nx-kb-legacy :deep(.nx-log-body) { padding-left: 0; font-size: 15px; line-height: 1.85; }
.nx-kb-legacy :deep(.nx-log-body:not(.user-text)) {
  background: linear-gradient(180deg, #ecfeff 0%, #f0fdfa 100%);
  border: 1px solid rgba(13, 148, 136, 0.22);
  border-left: 3px solid var(--nx-accent-teal);
  color: #164e63;
  border-radius: 8px;
  padding: 18px 22px;
  box-shadow: 0 10px 28px rgba(13, 148, 136, 0.08);
}
.nx-kb-legacy :deep(.nx-log-body.user-text) {
  max-width: 880px;
  margin-left: auto;
  background: var(--nx-accent-amber-dim);
  border: 1px solid rgba(245, 158, 11, 0.28);
  border-radius: 8px;
  padding: 12px 16px;
}
.nx-kb-legacy :deep(.nx-log-body:not(.user-text) .nx-markdown),
.nx-kb-legacy :deep(.nx-log-body:not(.user-text) .nx-markdown p),
.nx-kb-legacy :deep(.nx-log-body:not(.user-text) .nx-markdown li) {
  color: #164e63;
}
.nx-kb-legacy :deep(.nx-markdown strong) { color: #0f766e; font-weight: 700; }
.nx-kb-legacy :deep(.nx-markdown blockquote) {
  background: rgba(245, 158, 11, 0.08);
  border-left-color: var(--nx-accent-amber);
  border-radius: 0 6px 6px 0;
  padding: 10px 14px;
  font-style: normal;
}
.nx-kb-legacy :deep(.nx-sources-box) { max-width: 1120px; margin: 12px auto 0; border-radius: 8px; }
.nx-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 14px;
  background: var(--nx-bg-surface);
  border: 1px solid var(--nx-border);
  border-radius: 8px;
}
.nx-input-prompt { font-family: var(--nx-font-mono); font-size: 16px; color: var(--nx-accent-amber); font-weight: 600; }

@media (max-width: 900px) {
  .nx-kb-legacy { height: auto; }
  .nx-rag-card :deep(.el-card__body) { height: auto; }
  .nx-rag-legacy-log { height: 56vh; min-height: 340px; padding: 16px; }
  .nx-kb-legacy :deep(.nx-log-body:not(.user-text)) { padding: 14px 16px; }
}
</style>
