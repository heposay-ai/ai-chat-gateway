<template>
  <div class="nx-exec-timeline">
    <div class="timeline-header">
      <span class="nx-nav-prefix">[LOG]</span>
      <span>执行日志</span>
      <span class="timeline-status nx-mono" :class="status.toLowerCase()">{{ status }}</span>
    </div>

    <div class="timeline-body" ref="scrollBox">
      <div v-if="logs.length === 0" class="timeline-empty">
        暂无执行日志
      </div>

      <div v-for="(log, i) in logs" :key="i" class="timeline-item">
        <span class="item-dot" :class="dotClass(log)"></span>
        <div class="item-content">
          <div class="item-event nx-mono">{{ log.event }}</div>
          <div class="item-detail" v-if="log.data.nodeKey">
            <span class="item-node">{{ log.data.nodeKey }}</span>
            <span v-if="log.data.durationMs" class="item-duration">{{ log.data.durationMs }}ms</span>
          </div>
          <div class="item-detail" v-if="log.data.content">
            <span class="item-token">{{ log.data.content }}</span>
          </div>
          <div class="item-detail" v-if="log.data.error">
            <span class="item-error">{{ log.data.error }}</span>
          </div>
          <div class="item-time nx-mono">{{ formatTime(log.timestamp) }}</div>
        </div>
      </div>
    </div>

    <div class="timeline-footer" v-if="totalDuration > 0">
      <span class="nx-mono">TOTAL: {{ totalDuration }}ms</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  logs: { type: Array, default: () => [] },
  status: { type: String, default: 'IDLE' },
  totalDuration: { type: Number, default: 0 },
})

const scrollBox = ref(null)

watch(() => props.logs.length, async () => {
  await nextTick()
  if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
})

function dotClass(log) {
  if (log.event === 'execution_error' || log.data?.status === 'FAILED') return 'dot-error'
  if (log.event === 'node_end' && log.data?.status === 'COMPLETED') return 'dot-success'
  if (log.event === 'execution_complete') return 'dot-success'
  if (log.event === 'node_start' || log.event === 'execution_start') return 'dot-running'
  if (log.event === 'node_skipped') return 'dot-skipped'
  if (log.event === 'execution_paused') return 'dot-paused'
  return 'dot-default'
}

function formatTime(ts) {
  return ts ? ts.split('T')[1]?.substring(0, 12) || ts : ''
}
</script>

<style scoped>
.nx-exec-timeline {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--nx-bg-surface);
  border-left: 1px solid var(--nx-border);
}

.timeline-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--nx-border);
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--nx-font-mono);
  font-size: 11px;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 2px;
}

.timeline-status {
  margin-left: auto;
  font-size: 10px;
  letter-spacing: 1px;
  padding: 2px 8px;
  border-radius: 3px;
}
.timeline-status.running { color: var(--nx-accent-amber); background: rgba(245,158,11,0.15); }
.timeline-status.completed { color: var(--nx-accent-teal); background: rgba(45,212,191,0.15); }
.timeline-status.failed { color: var(--nx-accent-rose); background: rgba(244,63,94,0.15); }
.timeline-status.paused { color: var(--nx-accent-amber); background: rgba(245,158,11,0.15); }

.timeline-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.timeline-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--nx-text-muted);
  font-family: var(--nx-font-mono);
  font-size: 11px;
}

.timeline-item {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--nx-border);
}
.timeline-item:last-child { border-bottom: none; }

.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 4px;
  flex-shrink: 0;
}
.dot-running { background: var(--nx-accent-amber); box-shadow: 0 0 6px var(--nx-accent-amber); }
.dot-success { background: var(--nx-accent-teal); }
.dot-error { background: var(--nx-accent-rose); }
.dot-skipped { background: var(--nx-text-muted); opacity: 0.4; }
.dot-paused { background: var(--nx-accent-amber); }
.dot-default { background: var(--nx-text-muted); }

.item-content { flex: 1; min-width: 0; }
.item-event { font-size: 11px; color: var(--nx-text-primary); font-weight: 600; letter-spacing: 0.5px; }
.item-detail { font-size: 11px; color: var(--nx-text-secondary); margin-top: 2px; }
.item-node { font-family: var(--nx-font-mono); color: var(--nx-accent-teal); }
.item-duration { margin-left: 8px; font-family: var(--nx-font-mono); opacity: 0.6; }
.item-token { color: var(--nx-text-secondary); word-break: break-all; }
.item-error { color: var(--nx-accent-rose); }
.item-time { font-size: 10px; color: var(--nx-text-muted); margin-top: 2px; }

.timeline-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--nx-border);
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
}
</style>
