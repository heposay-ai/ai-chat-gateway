<template>
  <transition name="nx-slide">
    <div class="nx-config-panel" v-if="node">
      <div class="panel-header">
        <span class="panel-title">{{ node.data?.label || '节点' }} 配置</span>
        <span class="panel-close" @click="$emit('close')">x</span>
      </div>

      <div class="panel-body">
        <!-- 通用：节点名称 -->
        <div class="form-group">
          <label>节点名称</label>
          <el-input v-model="node.data.label" size="small" />
        </div>

        <!-- AGENT 节点 -->
        <template v-if="nodeType === 'AGENT'">
          <div class="form-group">
            <label>选择 Agent</label>
            <el-select v-model="node.data.agentId" size="small" style="width: 100%" placeholder="选择 Agent">
              <el-option v-for="a in agents" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </div>
          <div class="form-group">
            <label>Prompt 模板</label>
            <el-input v-model="node.data.prompt" type="textarea" :rows="4" size="small"
              placeholder="可用变量: {{userInput}}, {{nodeKey_output}}" />
          </div>
        </template>

        <!-- TOOL 节点 -->
        <template v-if="nodeType === 'TOOL'">
          <div class="form-group">
            <label>工具名称</label>
            <el-select v-model="node.data.toolName" size="small" style="width: 100%">
              <el-option value="web_search" label="联网搜索" />
              <el-option value="url_reader" label="网页读取" />
              <el-option value="knowledge_base" label="知识库查询" />
              <el-option value="current_time" label="当前时间" />
              <el-option value="code_interpreter" label="代码计算" />
            </el-select>
          </div>
          <div class="form-group">
            <label>输入模板</label>
            <el-input v-model="node.data.inputTemplate" size="small" placeholder="{{userInput}}" />
          </div>
        </template>

        <!-- CONDITION 节点 -->
        <template v-if="nodeType === 'CONDITION'">
          <div class="form-group">
            <label>SpEL 表达式</label>
            <el-input v-model="node.data.expr" size="small" placeholder="#output.length() > 500" />
          </div>
          <div class="form-hint">可用: #userInput, #nodeKey_output, #output</div>
        </template>

        <!-- HTTP 节点 -->
        <template v-if="nodeType === 'HTTP'">
          <div class="form-group">
            <label>请求方法</label>
            <el-select v-model="node.data.method" size="small" style="width: 100%">
              <el-option value="GET" /><el-option value="POST" />
              <el-option value="PUT" /><el-option value="DELETE" />
            </el-select>
          </div>
          <div class="form-group">
            <label>URL</label>
            <el-input v-model="node.data.url" size="small" placeholder="https://api.example.com/..." />
          </div>
          <div class="form-group">
            <label>请求头 (JSON)</label>
            <el-input v-model="node.data.headers" type="textarea" :rows="2" size="small"
              placeholder='{"Authorization": "Bearer ..."}' />
          </div>
          <div class="form-group">
            <label>请求体 (JSON)</label>
            <el-input v-model="node.data.body" type="textarea" :rows="3" size="small"
              placeholder='{"text": "{{nodeKey_output}}"}' />
          </div>
        </template>

        <!-- KNOWLEDGE 节点 -->
        <template v-if="nodeType === 'KNOWLEDGE'">
          <div class="form-group">
            <label>知识库 ID</label>
            <el-input v-model="node.data.knowledgeBaseId" size="small" placeholder="知识库 ID" />
          </div>
          <div class="form-group">
            <label>查询模板</label>
            <el-input v-model="node.data.query" size="small" placeholder="{{userInput}}" />
          </div>
          <div class="form-group">
            <label>Top K</label>
            <el-input-number v-model="node.data.topK" :min="1" :max="20" size="small" />
          </div>
        </template>

        <!-- CODE 节点 -->
        <template v-if="nodeType === 'CODE'">
          <div class="form-group">
            <label>JavaScript 代码</label>
            <el-input v-model="node.data.code" type="textarea" :rows="8" size="small"
              placeholder="// 可用变量: input (上一节点输出)&#10;var result = input.toUpperCase();&#10;result;" />
          </div>
        </template>

        <!-- HUMAN_REVIEW 节点 -->
        <template v-if="nodeType === 'HUMAN_REVIEW'">
          <div class="form-group">
            <label>审批提示</label>
            <el-input v-model="node.data.prompt" type="textarea" :rows="2" size="small"
              placeholder="请审核以下内容" />
          </div>
          <div class="form-group">
            <label>超时时间 (秒)</label>
            <el-input-number v-model="node.data.timeout" :min="60" :max="86400" size="small" />
          </div>
        </template>

        <!-- PARALLEL 节点 -->
        <template v-if="nodeType === 'PARALLEL'">
          <div class="form-hint">并行节点将同时执行所有下游分支，无需额外配置。</div>
        </template>

        <!-- 删除按钮 -->
        <div class="form-group" style="margin-top: 24px">
          <el-popconfirm title="删除此节点？" @confirm="$emit('delete', node.id)">
            <template #reference>
              <el-button type="danger" size="small" plain style="width: 100%">删除节点</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listAgents } from '@/api/agent'

const props = defineProps({
  node: { type: Object, default: null },
})
defineEmits(['close', 'delete'])

const nodeType = computed(() => props.node?.data?.nodeType || '')

const agents = ref([])

onMounted(async () => {
  try {
    const res = await listAgents()
    agents.value = Array.isArray(res) ? res : (res.data || [])
  } catch { /* ignore */ }
})
</script>

<style scoped>
.nx-config-panel {
  width: 280px;
  background: var(--nx-bg-surface);
  border-left: 1px solid var(--nx-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--nx-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--nx-text-primary);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.panel-close {
  font-family: var(--nx-font-mono);
  font-size: 14px;
  cursor: pointer;
  color: var(--nx-text-muted);
  transition: color 150ms;
}
.panel-close:hover { color: var(--nx-accent-rose); }

.panel-body {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: 14px;
}

.form-group label {
  display: block;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.form-hint {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  margin-top: 4px;
  letter-spacing: 0.5px;
}

/* 滑入动画 */
.nx-slide-enter-active,
.nx-slide-leave-active { transition: all 200ms ease-out; }
.nx-slide-enter-from,
.nx-slide-leave-to { transform: translateX(100%); opacity: 0; }
</style>
