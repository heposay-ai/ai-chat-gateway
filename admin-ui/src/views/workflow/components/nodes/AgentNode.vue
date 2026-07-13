<template>
  <div class="wf-node agent-node" :class="{ selected }">
    <Handle type="target" :position="Position.Left" />
    <div class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-label">{{ data.label || 'Agent' }}</span>
    </div>
    <div class="node-body">
      <div class="node-info" v-if="data.agentName || data.agentId">
        {{ data.agentName || `Agent #${data.agentId}` }}
      </div>
      <div class="node-info text-muted" v-else>点击配置 Agent</div>
    </div>
    <div class="node-status" v-if="data.executionStatus">
      <span :class="'st-' + data.executionStatus.toLowerCase()">{{ statusIcon }}</span>
      <span v-if="data.durationMs" class="duration">{{ data.durationMs }}ms</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const statusIcon = computed(() => {
  const m = { RUNNING: '...', COMPLETED: 'OK', FAILED: 'ERR', SKIPPED: 'SKIP' }
  return m[props.data.executionStatus] || ''
})
</script>

<style scoped>
.wf-node { min-width: 160px; border-radius: 6px; border: 2px solid transparent; background: var(--nx-bg-surface, #1a1a2e); color: var(--nx-text-primary, #e0e0e0); font-size: 13px; box-shadow: 0 2px 8px rgba(0,0,0,0.3); }
.wf-node.selected { border-color: var(--nx-accent-amber, #f59e0b); box-shadow: 0 0 0 2px rgba(245,158,11,0.3); }
.node-header { padding: 8px 12px; border-radius: 4px 4px 0 0; display: flex; align-items: center; gap: 6px; }
.agent-node .node-header { background: rgba(24,144,255,0.15); }
.node-icon { font-size: 16px; }
.node-label { font-weight: 600; font-family: var(--nx-font-mono); font-size: 12px; }
.node-body { padding: 6px 12px; }
.node-info { font-size: 12px; opacity: 0.8; }
.text-muted { opacity: 0.5; }
.node-status { padding: 4px 12px 6px; font-family: var(--nx-font-mono); font-size: 10px; display: flex; align-items: center; gap: 6px; }
.duration { opacity: 0.6; }
.st-running { color: var(--nx-accent-amber); animation: nx-pulse 1s infinite; }
.st-completed { color: var(--nx-accent-teal); }
.st-failed { color: var(--nx-accent-rose); }
.st-skipped { opacity: 0.4; }
@keyframes nx-pulse { 50% { opacity: 0.5; } }
</style>
