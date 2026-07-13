<template>
  <div class="nx-rag-chat">
    <!-- 会话侧栏 -->
    <div class="nx-session-sidebar">
      <div class="nx-session-header">
        <el-button type="primary" size="small" @click="newSession" style="width: 100%">
          + 新建会话
        </el-button>
      </div>
      <div class="nx-session-list">
        <div v-for="s in sessions" :key="s.id"
             :class="['nx-session-item', { active: s.id === currentSessionId }]"
             @click="switchSession(s.id)">
          <span class="nx-session-title">{{ s.title || '新会话' }}</span>
          <span class="nx-session-del" @click.stop="handleDeleteSession(s.id)">&times;</span>
        </div>
        <div v-if="sessions.length === 0" class="nx-session-empty">暂无会话</div>
      </div>
      <div class="nx-session-footer">
        <el-button link size="small" @click="$router.push(`/knowledge/${kbId}`)"
          style="color: var(--nx-text-muted); font-family: var(--nx-font-mono); font-size: 11px">
          &lt; 返回知识库
        </el-button>
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="nx-chat-main">
      <div class="nx-chat-header">
        <span class="nx-section-title">{{ kbName || '知识库' }} // RAG 对话</span>
        <el-select v-model="model" size="small" style="width: 160px">
          <el-option label="DeepSeek Chat" value="deepseek-chat" />
          <el-option label="通义千问" value="qwen-plus" />
          <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
          <el-option label="Claude Sonnet" value="claude-sonnet" />
        </el-select>
      </div>

      <div class="nx-log-area" ref="messagesRef">
        <div v-if="messages.length === 0" class="nx-empty">
          <div class="nx-empty-icon">_</div>
          <div>上传文档 // 开始提问</div>
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
          <!-- Sources -->
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
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKb, listSessions, getSessionMessages, deleteSession } from '@/api/rag'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => route.params.id)
const kbName = ref('')

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputMsg = ref('')
const model = ref('deepseek-chat')
const isStreaming = ref(false)
const messagesRef = ref(null)

function normalizeMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```([A-Za-z0-9_-]+)[ \t]+/g, '```$1\n')
    .replace(/[ \t]+```/g, '\n```')
}

function renderMd(text) { return renderMarkdown(normalizeMarkdown(text)) }
function now() { return new Date().toLocaleTimeString('en-GB', { hour12: false }) }

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function loadKbInfo() {
  try { const kb = await getKb(kbId.value); kbName.value = kb.name }
  catch (e) { router.push('/knowledge-list') }
}

async function loadSessions() {
  try { sessions.value = await listSessions(kbId.value) } catch (e) {}
}

async function switchSession(sessionId) {
  currentSessionId.value = sessionId
  try {
    const msgs = await getSessionMessages(kbId.value, sessionId)
    messages.value = msgs.map(m => ({
      role: m.role, content: m.content, time: '',
      sources: m.sources ? JSON.parse(m.sources) : []
    }))
    scrollToBottom()
  } catch (e) {}
}

function newSession() { currentSessionId.value = null; messages.value = [] }

async function handleDeleteSession(sessionId) {
  try {
    await deleteSession(kbId.value, sessionId)
    ElMessage.success('已删除')
    if (currentSessionId.value === sessionId) { currentSessionId.value = null; messages.value = [] }
    loadSessions()
  } catch (e) {}
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
    const params = new URLSearchParams({ q: question, model: model.value })
    if (currentSessionId.value) params.set('sessionId', currentSessionId.value)
    const url = `/api/rag/kb/${kbId.value}/stream?${params}`
    const resp = await fetch(url, { headers: { 'Authorization': `Bearer ${apiKey}` } })

    if (!resp.ok) {
      messages.value[messages.value.length - 1].content =
        resp.status === 401 ? '[错误] 未授权' : `[错误] ${resp.status}`
      isStreaming.value = false; return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let nextEvent = null

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) { nextEvent = trimmed.slice(6).trim(); continue }
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          if (nextEvent === 'sources' && Array.isArray(parsed)) last.sources = parsed
          else if (nextEvent === 'session' && parsed.sessionId) currentSessionId.value = parsed.sessionId
          else if (parsed.content) last.content += parsed.content
          nextEvent = null
          scrollToBottom()
        } catch (e) {}
      }
    }
    isStreaming.value = false
    loadSessions()
  } catch (e) {
    isStreaming.value = false
    const last = messages.value[messages.value.length - 1]
    if (!last.content) last.content = '[错误] ' + (e.message || '网络故障')
  }
}

onMounted(() => { loadKbInfo(); loadSessions() })
</script>

<style scoped>
.nx-rag-chat {
  display: flex;
  height: calc(100vh - 24px);
  min-height: 760px;
  gap: 0;
}

/* Session sidebar */
.nx-session-sidebar {
  width: 240px;
  background: var(--nx-bg-surface);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  margin-right: 16px;
}
.nx-session-header { padding: 12px; border-bottom: 1px solid var(--nx-border); }
.nx-session-list { flex: 1; overflow-y: auto; padding: 6px; }
.nx-session-item {
  padding: 8px 10px;
  border-radius: 2px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--nx-text-secondary);
  margin-bottom: 2px;
  border-left: 3px solid transparent;
  transition: all 150ms;
}
.nx-session-item:hover { background: var(--nx-bg-raised); color: var(--nx-text-primary); }
.nx-session-item.active {
  background: var(--nx-bg-raised);
  color: var(--nx-accent-amber);
  border-left-color: var(--nx-accent-amber);
}
.nx-session-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.nx-session-del { opacity: 0; color: var(--nx-text-muted); cursor: pointer; font-size: 16px; }
.nx-session-item:hover .nx-session-del { opacity: 1; }
.nx-session-empty {
  text-align: center; color: var(--nx-text-muted); font-family: var(--nx-font-mono);
  font-size: 11px; padding: 20px 0; letter-spacing: 1px;
}
.nx-session-footer { padding: 10px 12px; border-top: 1px solid var(--nx-border); }

/* Chat main */
.nx-chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.nx-chat-header {
  padding: 12px 18px;
  background: var(--nx-bg-surface);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  flex-shrink: 0;
}

.nx-log-area {
  flex: 1;
  min-height: 0;
  margin-bottom: 14px;
  padding: 22px 28px;
  scroll-behavior: smooth;
}

.nx-rag-chat :deep(.nx-log-entry) {
  max-width: 1120px;
  margin: 0 auto 24px;
}

.nx-rag-chat :deep(.nx-log-body) {
  padding-left: 0;
  font-size: 15px;
  line-height: 1.85;
}

.nx-rag-chat :deep(.nx-log-role.assistant) {
  color: var(--nx-accent-teal);
}

.nx-rag-chat :deep(.nx-log-body:not(.user-text)) {
  background: linear-gradient(180deg, #ecfeff 0%, #f0fdfa 100%);
  border: 1px solid rgba(13, 148, 136, 0.22);
  border-left: 3px solid var(--nx-accent-teal);
  color: #164e63;
  border-radius: 8px;
  padding: 18px 22px;
  box-shadow: 0 10px 28px rgba(13, 148, 136, 0.08);
}

.nx-rag-chat :deep(.nx-log-body.user-text) {
  max-width: 880px;
  margin-left: auto;
  background: var(--nx-accent-amber-dim);
  border: 1px solid rgba(245, 158, 11, 0.28);
  border-radius: 8px;
  padding: 12px 16px;
}

.nx-rag-chat :deep(.nx-markdown) {
  max-width: none;
  color: #164e63;
  word-break: break-word;
}

.nx-rag-chat :deep(.nx-markdown p) {
  color: #164e63;
  margin-bottom: 12px;
}

.nx-rag-chat :deep(.nx-markdown strong) {
  color: #0f766e;
  font-weight: 700;
}

.nx-rag-chat :deep(.nx-markdown li) {
  color: #164e63;
}

.nx-rag-chat :deep(.nx-markdown blockquote) {
  background: rgba(245, 158, 11, 0.08);
  border-left-color: var(--nx-accent-amber);
  border-radius: 0 6px 6px 0;
  padding: 10px 14px;
  font-style: normal;
}

.nx-rag-chat :deep(.nx-markdown pre) {
  margin: 14px 0;
  border-radius: 8px;
}

.nx-rag-chat :deep(.nx-sources-box) {
  max-width: 1120px;
  margin: 12px auto 0;
  border-radius: 8px;
}

.nx-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--nx-text-muted);
  font-family: var(--nx-font-mono);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 2px;
  gap: 12px;
}
.nx-empty-icon {
  font-size: 32px;
  color: var(--nx-accent-amber);
  animation: nx-pulse 2s infinite;
}

.nx-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
  padding: 12px 14px;
  background: var(--nx-bg-surface);
  border: 1px solid var(--nx-border);
  border-radius: 8px;
}
.nx-input-prompt {
  font-family: var(--nx-font-mono);
  font-size: 16px;
  color: var(--nx-accent-amber);
  font-weight: 600;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .nx-rag-chat { min-height: calc(100vh - 16px); }
  .nx-session-sidebar { display: none; }
  .nx-log-area { padding: 16px; }
  .nx-rag-chat :deep(.nx-log-body:not(.user-text)) { padding: 14px 16px; }
}
</style>
