<template>
  <div class="wf-node condition-node" :class="{ selected }">
    <Handle type="target" :position="Position.Left" />
    <div class="node-header">
      <span class="node-icon">❓</span>
      <span class="node-label">{{ data.label || '条件' }}</span>
    </div>
    <div class="node-body">
      <div class="condition-expr" v-if="data.expr">
        <code>{{ data.expr }}</code>
      </div>
      <div class="text-muted" v-else>点击配置条件</div>
    </div>
    <div class="node-status" v-if="data.executionStatus">
      <span :class="'st-' + data.executionStatus.toLowerCase()">{{ statusIcon }}</span>
    </div>
    <!-- 条件节点两个输出 -->
    <Handle type="source" :position="Position.Right" id="true" :style="{ top: '35%' }" />
    <Handle type="source" :position="Position.Right" id="false" :style="{ top: '65%' }" />
    <div class="branch-labels">
      <span class="branch-true">T</span>
      <span class="branch-false">F</span>
    </div>
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
.wf-node { min-width: 160px; border-radius: 6px; border: 2px solid transparent; background: var(--nx-bg-surface, #1a1a2e); color: var(--nx-text-primary, #e0e0e0); font-size: 13px; box-shadow: 0 2px 8px rgba(0,0,0,0.3); position: relative; }
.wf-node.selected { border-color: var(--nx-accent-amber, #f59e0b); box-shadow: 0 0 0 2px rgba(245,158,11,0.3); }
.node-header { padding: 8px 12px; border-radius: 4px 4px 0 0; display: flex; align-items: center; gap: 6px; }
.condition-node .node-header { background: rgba(114,46,209,0.15); }
.node-icon { font-size: 16px; }
.node-label { font-weight: 600; font-family: var(--nx-font-mono); font-size: 12px; }
.node-body { padding: 6px 12px; }
.condition-expr code { font-size: 11px; background: rgba(255,255,255,0.05); padding: 2px 6px; border-radius: 4px; word-break: break-all; font-family: var(--nx-font-mono); }
.text-muted { font-size: 12px; opacity: 0.5; }
.branch-labels { position: absolute; right: -28px; top: 0; height: 100%; display: flex; flex-direction: column; font-family: var(--nx-font-mono); font-size: 10px; font-weight: 700; }
.branch-true { position: absolute; right: 0; top: 30%; color: var(--nx-accent-teal, #52c41a); }
.branch-false { position: absolute; right: 0; top: 60%; color: var(--nx-accent-rose, #ff4d4f); }
.node-status { padding: 4px 12px 6px; font-family: var(--nx-font-mono); font-size: 10px; }
.st-running { color: var(--nx-accent-amber); animation: nx-pulse 1s infinite; }
.st-completed { color: var(--nx-accent-teal); }
.st-failed { color: var(--nx-accent-rose); }
.st-skipped { opacity: 0.4; }
@keyframes nx-pulse { 50% { opacity: 0.5; } }
</style>
