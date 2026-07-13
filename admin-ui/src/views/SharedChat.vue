<template>
  <div class="nx-shared">
    <div class="nx-shared-container">
      <!-- Header -->
      <div class="nx-shared-header">
        <div class="nx-shared-top">
          <div class="nx-shared-logo">NEXUS <span class="nx-amber">//</span> SHARE</div>
          <span class="nx-shared-theme-btn" @click="toggleTheme">{{ isDark ? '◑' : '◐' }}</span>
        </div>
        <h3 class="nx-shared-title">{{ kbInfo.name || '...' }}</h3>
        <div class="nx-shared-desc" v-if="kbInfo.description">{{ kbInfo.description }}</div>
      </div>

      <div v-if="error" class="nx-error-box">
        <span class="nx-mono">[ERROR]</span> {{ error }}
      </div>

      <template v-else>
        <div class="nx-log-area" ref="messagesRef">
          <div v-if="messages.length === 0" class="nx-empty">
            <div class="nx-empty-icon">_</div>
            <div>在下方输入您的问题</div>
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
          <el-input v-model="inputMsg" placeholder="输入问题..."
                    @keyup.enter="send" :disabled="isStreaming" />
          <el-button type="primary" @click="send" :loading="isStreaming">发送</el-button>
        </div>
      </template>

      <div class="nx-shared-footer">POWERED BY NEXUS CMD</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const token = route.params.token

const isDark = ref((localStorage.getItem('nx-theme') || 'dark') === 'dark')
function toggleTheme() {
  isDark.value = !isDark.value
  const theme = isDark.value ? 'dark' : 'light'
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('nx-theme', theme)
}

const kbInfo = ref({})
const messages = ref([])
const inputMsg = ref('')
const isStreaming = ref(false)
const messagesRef = ref(null)
const error = ref('')

function renderMd(text) { return renderMarkdown(text) }
function now() { return new Date().toLocaleTimeString('en-GB', { hour12: false }) }

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function loadInfo() {
  try {
    const resp = await fetch(`/api/rag/share/${token}/info`)
    if (!resp.ok) throw new Error()
    kbInfo.value = await resp.json()
  } catch (e) { error.value = '分享链接无效或已关闭' }
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return
  const question = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: question, time: now() })
  messages.value.push({ role: 'assistant', content: '', sources: [], time: now() })
  scrollToBottom()

  isStreaming.value = true
  try {
    const url = `/api/rag/share/${token}/stream?q=${encodeURIComponent(question)}`
    const resp = await fetch(url)
    if (!resp.ok) {
      messages.value[messages.value.length - 1].content = `[ERROR] ${resp.status}`
      isStreaming.value = false; return
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
        if (!data || data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          if (nextEvent === 'sources' && Array.isArray(parsed)) last.sources = parsed
          else if (parsed.content) last.content += parsed.content
          nextEvent = null
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

onMounted(loadInfo)
</script>

<style scoped>
.nx-shared {
  min-height: 100vh;
  background-color: var(--nx-bg-deep);
  background-image:
    linear-gradient(var(--nx-grid-line, rgba(30, 45, 61, 0.2)) 1px, transparent 1px),
    linear-gradient(90deg, var(--nx-grid-line, rgba(30, 45, 61, 0.2)) 1px, transparent 1px);
  background-size: 40px 40px;
  display: flex;
  justify-content: center;
  padding: 24px;
}

.nx-shared-container {
  max-width: 860px;
  width: 100%;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
}

.nx-shared-header {
  text-align: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--nx-border);
  margin-bottom: 16px;
}
.nx-shared-top {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  position: relative;
}
.nx-shared-theme-btn {
  position: absolute;
  right: 0;
  cursor: pointer;
  font-size: 18px;
  color: var(--nx-text-muted);
  transition: color 150ms;
}
.nx-shared-theme-btn:hover {
  color: var(--nx-accent-amber);
}
.nx-shared-logo {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--nx-text-muted);
  margin-bottom: 6px;
}
.nx-amber { color: var(--nx-accent-amber); }
.nx-shared-title {
  margin: 0;
  color: var(--nx-text-primary);
  font-size: 18px;
  font-weight: 600;
}
.nx-shared-desc {
  color: var(--nx-text-secondary);
  font-size: 13px;
  margin-top: 4px;
}

.nx-error-box {
  text-align: center;
  color: var(--nx-accent-rose);
  padding: 60px 0;
  font-family: var(--nx-font-mono);
  font-size: 14px;
}

.nx-log-area { flex: 1; margin-bottom: 12px; }

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
.nx-empty-icon { font-size: 32px; color: var(--nx-accent-amber); animation: nx-pulse 2s infinite; }

.nx-input-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nx-input-prompt {
  font-family: var(--nx-font-mono);
  font-size: 16px;
  color: var(--nx-accent-amber);
  font-weight: 600;
}

.nx-shared-footer {
  text-align: center;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  letter-spacing: 2px;
  padding: 12px 0;
  margin-top: 8px;
}
</style>
