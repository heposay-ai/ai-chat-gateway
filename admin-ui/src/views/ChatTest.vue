<template>
  <div class="nx-chat-page">
    <!-- 工具栏 -->
    <div class="nx-toolbar nx-panel">
      <div class="nx-toolbar-left">
        <span class="nx-section-title">直接对话</span>
      </div>
      <div class="nx-toolbar-right">
        <el-select v-model="model" size="small" style="width: 180px">
          <el-option label="DeepSeek Chat" value="deepseek-chat" />
          <el-option label="通义千问" value="qwen-plus" />
          <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
          <el-option label="Claude Sonnet" value="claude-sonnet" />
        </el-select>
        <el-select v-model="kbId" size="small" style="width: 150px" clearable placeholder="引用知识库">
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button size="small" @click="showConfig = !showConfig">
          {{ showConfig ? '收起配置' : '自定义配置' }}
        </el-button>
        <el-button size="small" @click="clearChat">清空</el-button>
      </div>
    </div>

    <!-- 自定义配置面板 -->
    <div v-if="showConfig" class="nx-config-panel">
      <el-input v-model="customBaseUrl" size="small" placeholder="Base URL（留空使用默认）" clearable style="flex: 2" />
      <el-input v-model="customApiKey" size="small" placeholder="API Key（留空使用默认）" clearable show-password style="flex: 2" />
      <el-input v-model="customModelName" size="small" placeholder="模型名称（留空使用默认）" clearable style="flex: 1" />
      <el-button size="small" type="success" @click="saveConfig">保存</el-button>
    </div>

    <!-- 消息日志区 -->
    <div class="nx-log-area" ref="messagesRef">
      <div v-if="messages.length === 0" class="nx-empty">
        <div class="nx-empty-icon">_</div>
        <div>选择模型 // 开始对话</div>
      </div>

      <div v-for="(msg, i) in messages" :key="i" class="nx-log-entry">
        <div class="nx-log-header">
          <span :class="['nx-log-role', msg.role]">
            {{ msg.role === 'user' ? '> 用户' : '  系统' }}
          </span>
          <span class="nx-log-time">{{ msg.time }}</span>
          <span class="nx-log-rule"></span>
        </div>
        <div class="nx-log-body" :class="{ 'user-text': msg.role === 'user' }">
          <template v-if="msg.role === 'user'">{{ msg.content }}</template>
          <template v-else>
            <div class="nx-markdown" v-html="renderMd(msg.content)"></div>
            <span v-if="i === messages.length - 1 && isStreaming" class="nx-log-cursor"></span>
          </template>
        </div>
      </div>
    </div>

    <!-- 输入栏 -->
    <div class="nx-input-bar">
      <span class="nx-input-prompt">></span>
      <el-input v-model="inputMsg" placeholder="输入消息..."
        @keyup.enter="send" :disabled="isStreaming" />
      <el-button type="primary" @click="send" :loading="isStreaming">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { listKbs } from '@/api/rag'

const CHAT_STORAGE_KEY = 'nx-chat-test-messages'
const MODEL_STORAGE_KEY = 'nx-chat-test-model'
const KB_STORAGE_KEY = 'nx-chat-test-kb'
const MAX_SAVED_MESSAGES = 100

const messages = ref([])
const inputMsg = ref('')
const model = ref('deepseek-chat')
const kbId = ref(null)
const kbList = ref([])
const isStreaming = ref(false)
const messagesRef = ref(null)
const showConfig = ref(false)
const customBaseUrl = ref('')
const customApiKey = ref('')
const customModelName = ref('')

function saveConfig() {
  localStorage.setItem('chatModelConfig', JSON.stringify({
    baseUrl: customBaseUrl.value,
    apiKey: customApiKey.value,
    modelName: customModelName.value
  }))
  showConfig.value = false
}

function normalizeMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```([A-Za-z0-9_-]+)[ \t]+/g, '```$1\n')
    .replace(/[ \t]+```/g, '\n```')
}

function renderMd(text) { return renderMarkdown(normalizeMarkdown(text)) }

function now() {
  return new Date().toLocaleTimeString('en-GB', { hour12: false })
}

function clearChat() {
  messages.value = []
  localStorage.removeItem(CHAT_STORAGE_KEY)
}

function loadChatState() {
  const savedModel = localStorage.getItem(MODEL_STORAGE_KEY)
  if (savedModel) model.value = savedModel

  const savedKb = localStorage.getItem(KB_STORAGE_KEY)
  if (savedKb) kbId.value = Number(savedKb)

  try {
    const savedMessages = JSON.parse(localStorage.getItem(CHAT_STORAGE_KEY) || '[]')
    if (Array.isArray(savedMessages)) messages.value = savedMessages
  } catch (e) {
    localStorage.removeItem(CHAT_STORAGE_KEY)
  }
}

function saveChatState() {
  const recentMessages = messages.value.slice(-MAX_SAVED_MESSAGES)
  localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(recentMessages))
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return

  const userMsg = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: userMsg, time: now() })
  messages.value.push({ role: 'assistant', content: '', time: now() })
  scrollToBottom()

  isStreaming.value = true
  const apiKey = localStorage.getItem('apiKey') || ''

  try {
    const params = new URLSearchParams({ message: userMsg, model: model.value })
    if (kbId.value) params.set('kbId', kbId.value)
    if (customBaseUrl.value) params.set('baseUrl', customBaseUrl.value)
    if (customApiKey.value) params.set('apiKey', customApiKey.value)
    if (customModelName.value) params.set('modelName', customModelName.value)
    const url = `/api/chat/stream?${params.toString()}`
    const resp = await fetch(url, {
      headers: { 'Authorization': `Bearer ${apiKey}` }
    })

    if (!resp.ok) {
      const last = messages.value[messages.value.length - 1]
      last.content = resp.status === 401 ? '[错误] 未授权 — 请在侧栏设置 API Key' : `[错误] 请求失败 (${resp.status})`
      isStreaming.value = false
      return
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

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()
        if (data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          if (parsed.error) {
            last.content = `[错误] ${parsed.error}`
          } else {
            last.content += parsed.content || ''
          }
          scrollToBottom()
        } catch (e) { /* ignore */ }
      }
    }

    isStreaming.value = false
  } catch (e) {
    isStreaming.value = false
  }
}

async function fetchKbList() {
  try {
    kbList.value = await listKbs()
  } catch (e) { /* ignore */ }
}

onMounted(() => {
  loadChatState()
  fetchKbList()
  const saved = localStorage.getItem('chatModelConfig')
  if (saved) {
    try {
      const config = JSON.parse(saved)
      customBaseUrl.value = config.baseUrl || ''
      customApiKey.value = config.apiKey || ''
      customModelName.value = config.modelName || ''
    } catch (e) { /* ignore */ }
  }
  scrollToBottom()
})

watch(messages, saveChatState, { deep: true })
watch(model, value => localStorage.setItem(MODEL_STORAGE_KEY, value))
watch(kbId, value => localStorage.setItem(KB_STORAGE_KEY, value != null ? String(value) : ''))
</script>

<style scoped>
.nx-chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  gap: 0;
}

.nx-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  margin-bottom: 0;
  border-bottom: none;
  flex-shrink: 0;
}
.nx-toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.nx-log-area {
  flex: 1;
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  padding: 20px;
  overflow-y: auto;
  margin: 12px 0;
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

.nx-config-panel {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 16px;
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
}

.nx-input-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.nx-input-prompt {
  font-family: var(--nx-font-mono);
  font-size: 16px;
  color: var(--nx-accent-amber);
  font-weight: 600;
  flex-shrink: 0;
}
</style>
