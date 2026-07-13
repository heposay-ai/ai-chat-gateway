<template>
  <div class="nx-chat-page">
    <!-- 工具栏 -->
    <div class="nx-toolbar nx-panel">
      <div class="nx-toolbar-left">
        <span class="nx-section-title">MCP 工具测试</span>
      </div>
      <div class="nx-toolbar-right">
        <el-select v-model="selectedPreset" size="small" style="width: 200px" @change="onPresetChange">
          <el-option v-for="p in presets" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
        <el-input v-model="serverUrl" size="small" placeholder="MCP 服务器地址 (SSE)" style="width: 360px" clearable />
        <el-button size="small" type="primary" @click="fetchTools">获取工具</el-button>
        <el-button size="small" @click="clearLog">清空</el-button>
      </div>
    </div>

    <!-- 工具列表 -->
    <div v-if="tools.length > 0" class="nx-tool-list">
      <div
        v-for="t in tools" :key="t.name"
        :class="['nx-tool-card', { active: activeTool === t.name }]"
        @click="selectTool(t)"
      >
        <span class="nx-tool-name">{{ t.name }}</span>
        <span class="nx-tool-desc">{{ t.description }}</span>
      </div>
    </div>

    <!-- 调用表单 -->
    <div v-if="activeTool" class="nx-call-panel nx-panel">
      <div class="nx-call-header">
        <span class="nx-call-title">调用 {{ activeTool }}</span>
        <el-button size="small" type="success" @click="callTool" :loading="isCalling">执行</el-button>
      </div>
      <div class="nx-params">
        <div v-for="p in activeParams" :key="p.name" class="nx-param-row">
          <label>{{ p.name }} <span v-if="p.required" class="nx-required">*</span></label>
          <el-input v-model="paramValues[p.name]" size="small" :placeholder="p.description || p.type" />
        </div>
      </div>
    </div>

    <!-- 日志区 -->
    <div class="nx-log-area" ref="logRef">
      <div v-if="logEntries.length === 0" class="nx-empty">
        <div class="nx-empty-icon">_</div>
        <div>连接 MCP 服务器 // 选择工具 // 开始测试</div>
      </div>
      <div v-for="(entry, i) in logEntries" :key="i" class="nx-log-entry">
        <div class="nx-log-header">
          <span :class="['nx-log-role', entry.type]">
            {{ entry.type === 'system' ? '> 系统' : entry.type === 'result' ? '  输出' : '  错误' }}
          </span>
          <span class="nx-log-time">{{ entry.time }}</span>
          <span class="nx-log-rule"></span>
        </div>
        <div class="nx-log-body">
          <span v-if="entry.type === 'system'">{{ entry.content }}</span>
          <template v-else-if="entry.type === 'result' && entry.markdown">
            <div class="nx-markdown nx-result-markdown" v-html="renderMd(entry.content)"></div>
            <div v-if="entry.paymentLinks?.length" class="nx-payment-links">
              <div v-for="link in entry.paymentLinks" :key="link.url" class="nx-payment-card">
                <div v-if="link.showQr" class="nx-payment-qr">
                  <QrcodeVue
                    :value="link.url"
                    :size="180"
                    level="L"
                    render-as="svg"
                    background="#ffffff"
                    foreground="#111827"
                  />
                </div>
                <div class="nx-payment-action">
                  <a :href="link.url" target="_blank" rel="noopener noreferrer">
                    {{ link.label || '打开支付页面' }}
                  </a>
                  <span>手机可直接点击，电脑可使用支付宝扫码</span>
                </div>
              </div>
            </div>
          </template>
          <pre v-else class="nx-log-json">{{ entry.content }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import QrcodeVue from 'qrcode.vue'
import { listMcpPresets, listMcpTools, callMcpTool } from '@/api/mcp'
import { renderMarkdown } from '@/utils/markdown'
import { extractHttpLinks, normalizeMcpResult } from '@/utils/mcpResult'

const serverUrl = ref('')
const selectedPreset = ref('')
const presets = ref([])
const tools = ref([])
const activeTool = ref('')
const activeParams = ref([])
const paramValues = ref({})
const isCalling = ref(false)
const logEntries = ref([])
const logRef = ref(null)

const now = () => new Date().toLocaleTimeString('en-GB', { hour12: false })

function addLog(type, content, details = {}) {
  logEntries.value.push({ type, content, time: now(), ...details })
  nextTick(() => {
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  })
}

const renderMd = text => renderMarkdown(text)

onMounted(async () => {
  try {
    presets.value = await listMcpPresets()
    if (presets.value.length > 0) {
      selectedPreset.value = presets.value[0].name
      serverUrl.value = presets.value[0].serverUrl
      activeParams.value = []
    }
  } catch (e) {
    addLog('error', '加载预置 MCP 服务失败: ' + e.message)
  }
})

function onPresetChange(name) {
  const p = presets.value.find(x => x.name === name)
  if (p) {
    serverUrl.value = p.serverUrl
    tools.value = []
    activeTool.value = ''
    activeParams.value = []
    addLog('system', '切换预置: ' + p.name + ' (' + p.description + ')')
  }
}

async function fetchTools() {
  if (!serverUrl.value.trim()) return
  addLog('system', '连接 MCP 服务器: ' + serverUrl.value)
  try {
    const result = await listMcpTools(serverUrl.value)
    tools.value = result.tools || []
    addLog('system', '获取到 ' + tools.value.length + ' 个工具')
  } catch (e) {
    addLog('error', '获取工具失败: ' + e.message)
  }
}

function selectTool(tool) {
  activeTool.value = tool.name
  // MCP 服务实时返回的 inputSchema 优先，预置仅用于兼容没有 schema 的服务。
  const preset = presets.value.find(p => p.name === selectedPreset.value)
  const presetParams = preset?.tools?.find(t => t.name === tool.name)?.parameters
  if (tool.inputSchema?.properties) {
    const required = tool.inputSchema.required || []
    activeParams.value = Object.entries(tool.inputSchema.properties).map(([k, v]) => ({
      name: k,
      type: v.type || 'string',
      description: v.description || '',
      required: required.includes(k)
    }))
  } else {
    activeParams.value = presetParams || []
  }
  paramValues.value = {}
}

async function callTool() {
  if (!activeTool.value) return

  const missingParams = activeParams.value
    .filter(p => p.required && (paramValues.value[p.name] === '' || paramValues.value[p.name] == null))
    .map(p => p.name)
  if (missingParams.length > 0) {
    addLog('error', '缺少必填参数: ' + missingParams.join(', '))
    return
  }

  // 构建参数对象（按类型转换）
  const args = {}
  for (const p of activeParams.value) {
    const val = paramValues.value[p.name]
    if (val === '' || val == null) continue
    if (p.type === 'number') {
      const numericValue = Number(val)
      if (!Number.isFinite(numericValue)) {
        addLog('error', `${p.name} 必须是有效数字`)
        return
      }
      args[p.name] = numericValue
    } else {
      args[p.name] = val
    }
  }

  isCalling.value = true
  addLog('system', '调用 ' + activeTool.value + ' → ' + JSON.stringify(args))

  try {
    const reader = await callMcpTool(serverUrl.value, activeTool.value, args)
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
        try {
          const parsed = JSON.parse(data)
          if (parsed.type === 'log') {
            addLog('system', parsed.message)
          } else if (parsed.type === 'result') {
            addResultLog(parsed.data)
          } else if (parsed.type === 'error') {
            addLog('error', parsed.message)
          }
        } catch (e) { /* ignore */ }
      }
    }
  } catch (e) {
    addLog('error', '调用失败: ' + e.message)
  } finally {
    isCalling.value = false
  }
}

function addResultLog(data) {
  const result = normalizeMcpResult(data)
  const shouldCreateQrCode = activeTool.value.startsWith('create-')
  const paymentLinks = shouldCreateQrCode
    ? extractHttpLinks(result.content)
      .filter(link => !link.isImage)
      .slice(0, 2)
      .map(link => ({ ...link, showQr: link.url.length <= 1800 }))
    : []

  addLog('result', result.content, {
    markdown: result.markdown,
    paymentLinks
  })
}

function clearLog() {
  logEntries.value = []
}
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
  flex-shrink: 0;
}
.nx-toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.nx-tool-list {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.nx-tool-card {
  padding: 8px 14px;
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
  transition: border-color .2s;
}
.nx-tool-card:hover, .nx-tool-card.active {
  border-color: var(--nx-accent-amber);
}
.nx-tool-name {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-accent-amber);
}
.nx-tool-desc {
  font-size: 11px;
  color: var(--nx-text-muted);
}

.nx-call-panel {
  padding: 14px 16px;
  margin: 0 0 12px 0;
  flex-shrink: 0;
}
.nx-call-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.nx-call-title {
  font-family: var(--nx-font-mono);
  font-size: 13px;
  color: var(--nx-accent-amber);
}
.nx-params {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.nx-param-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nx-param-row label {
  font-size: 11px;
  font-family: var(--nx-font-mono);
  color: var(--nx-text-secondary);
  white-space: nowrap;
  min-width: 100px;
  text-align: right;
}
.nx-required {
  color: #e74c3c;
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
@keyframes nx-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.nx-log-entry {
  margin-bottom: 16px;
}
.nx-log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.nx-log-role {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}
.nx-log-role.system { color: var(--nx-text-secondary); }
.nx-log-role.result { color: #27ae60; }
.nx-log-role.error { color: #e74c3c; }
.nx-log-time {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
}
.nx-log-rule {
  flex: 1;
  border-bottom: 1px dashed var(--nx-border);
}
.nx-log-body {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-text-main);
  line-height: 1.5;
}
.nx-log-json {
  margin: 0;
  padding: 10px;
  background: rgba(0,0,0,.2);
  border-radius: 2px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 11px;
}

.nx-result-markdown {
  padding: 10px;
  background: rgba(0,0,0,.2);
  border-radius: 2px;
  word-break: break-word;
}
.nx-payment-links {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.nx-payment-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  background: rgba(255,255,255,.04);
  border: 1px solid var(--nx-border);
  border-radius: 4px;
}
.nx-payment-qr {
  padding: 6px;
  background: #fff;
  border-radius: 4px;
  line-height: 0;
}
.nx-payment-action {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 260px;
}
.nx-payment-action a {
  color: var(--nx-signal-blue);
  font-weight: 600;
  word-break: break-word;
}
.nx-payment-action span {
  color: var(--nx-text-muted);
  font-size: 11px;
}

@media (max-width: 768px) {
  .nx-payment-card {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
