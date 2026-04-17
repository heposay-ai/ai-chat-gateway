<template>
  <div class="nx-page">
    <div class="nx-page-header">
      <h2 class="nx-title">代理网关</h2>
      <el-button @click="checkHealth" :loading="checking">刷新状态</el-button>
    </div>

    <!-- 网关状态卡片 -->
    <div class="nx-gateway-cards">
      <div class="nx-gw-card">
        <div class="nx-gw-card-label">网关端点</div>
        <div class="nx-gw-card-value">
          <code>{{ gatewayUrl }}</code>
          <el-button link @click="copyUrl" style="margin-left: 8px">复制</el-button>
        </div>
      </div>
      <div class="nx-gw-card">
        <div class="nx-gw-card-label">可用账号</div>
        <div class="nx-gw-card-value nx-gw-stat">
          <span class="nx-gw-num" :class="{ ok: healthData.healthyAccounts > 0 }">{{ healthData.healthyAccounts }}</span>
          <span class="nx-gw-sep">/</span>
          <span>{{ healthData.totalAccounts }}</span>
        </div>
      </div>
      <div class="nx-gw-card">
        <div class="nx-gw-card-label">网关状态</div>
        <div class="nx-gw-card-value">
          <el-tag :type="healthData.status === 'ok' ? 'success' : 'danger'" size="large">
            {{ healthData.status === 'ok' ? '运行中' : '异常' }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="nx-section">
      <h3 class="nx-subtitle">使用说明</h3>
      <div class="nx-usage-guide">
        <h4>1. Chat Completions (OpenAI 兼容)</h4>
        <pre class="nx-code-block">curl -X POST {{ gatewayUrl }}/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_KEY" \
  -d '{
    "model": "claude-4.5-sonnet",
    "messages": [{"role": "user", "content": "Hello"}]
  }'</pre>

        <h4>2. 获取可用模型</h4>
        <pre class="nx-code-block">curl {{ gatewayUrl }}/v1/models</pre>

        <h4>3. Claude Code 配置</h4>
        <pre class="nx-code-block">ANTHROPIC_BASE_URL="{{ gatewayUrl }}"
ANTHROPIC_AUTH_TOKEN="your-key"
ANTHROPIC_MODEL="claude-4.5-sonnet"</pre>

        <h4>4. 网关特性</h4>
        <ul class="nx-feature-list">
          <li>自动从健康账号中选择转发目标（加权随机）</li>
          <li>自动移除不兼容参数（thinking, betas, context_management 等）</li>
          <li>支持 SSE 流式响应透传</li>
          <li>请求日志自动记录（用于看板统计）</li>
          <li>预留负载均衡/故障转移扩展接口</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getGatewayHealth } from '../../api/proxy'

const checking = ref(false)
const gatewayUrl = ref(`${window.location.origin}/api/proxy/gateway`)

const healthData = reactive({
  status: 'unknown',
  totalAccounts: 0,
  healthyAccounts: 0
})

async function checkHealth() {
  checking.value = true
  try {
    const res = await getGatewayHealth()
    Object.assign(healthData, res)
  } catch {
    healthData.status = 'error'
  } finally {
    checking.value = false
  }
}

function copyUrl() {
  navigator.clipboard.writeText(gatewayUrl.value)
  ElMessage.success('已复制')
}

onMounted(checkHealth)
</script>

<style scoped>
.nx-page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.nx-title { font-family: var(--nx-font-mono); font-size: 18px; color: var(--nx-text-primary); }
.nx-subtitle { font-family: var(--nx-font-mono); font-size: 15px; color: var(--nx-text-primary); margin: 0 0 12px; }
.nx-section { margin-top: 32px; }

.nx-gateway-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.nx-gw-card {
  background: var(--nx-bg-surface); border: 1px solid var(--nx-border); border-radius: 4px; padding: 20px;
}
.nx-gw-card-label { font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 8px; }
.nx-gw-card-value { font-family: var(--nx-font-mono); font-size: 14px; color: var(--nx-text-primary); }
.nx-gw-card-value code { background: var(--nx-bg-deep); padding: 4px 8px; border-radius: 2px; font-size: 12px; }
.nx-gw-stat { font-size: 24px; font-weight: 600; }
.nx-gw-num.ok { color: var(--nx-accent-teal); }
.nx-gw-sep { color: var(--nx-text-muted); margin: 0 4px; }

.nx-usage-guide h4 {
  font-family: var(--nx-font-mono); font-size: 13px; color: var(--nx-accent-amber);
  margin: 20px 0 8px; letter-spacing: 0.5px;
}
.nx-code-block {
  background: var(--nx-bg-deep); color: var(--nx-text-primary); padding: 14px; border-radius: 4px;
  font-family: var(--nx-font-mono); font-size: 12px; overflow-x: auto; white-space: pre-wrap;
  border: 1px solid var(--nx-border);
}
.nx-feature-list { padding-left: 20px; font-size: 13px; color: var(--nx-text-secondary); line-height: 1.8; }
</style>
