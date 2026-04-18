<template>
  <div class="nx-node-palette">
    <div class="palette-header">
      <span class="nx-nav-prefix">[NOD]</span>
      <span>节点面板</span>
    </div>

    <div
      v-for="def in nodeDefinitions"
      :key="def.type"
      class="palette-item"
      draggable="true"
      @dragstart="onDragStart($event, def)"
    >
      <span class="item-icon" :style="{ backgroundColor: def.color + '20', color: def.color }">
        {{ def.icon }}
      </span>
      <div class="item-info">
        <div class="item-label">{{ def.label }}</div>
        <div class="item-desc">{{ def.description }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nodeDefinitions } from '../nodeTypes'

const onDragStart = (event, nodeDef) => {
  event.dataTransfer.setData('application/workflow-node', JSON.stringify(nodeDef))
  event.dataTransfer.effectAllowed = 'move'
}
</script>

<style scoped>
.nx-node-palette {
  width: 200px;
  background: var(--nx-bg-surface);
  border-right: 1px solid var(--nx-border);
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.palette-header {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 4px;
  border-radius: 4px;
  cursor: grab;
  border: 1px solid transparent;
  transition: all 150ms;
}

.palette-item:hover {
  background: var(--nx-bg-raised);
  border-color: var(--nx-border);
}

.palette-item:active { cursor: grabbing; }

.item-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 16px;
  flex-shrink: 0;
}

.item-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--nx-text-primary);
}

.item-desc {
  font-size: 10px;
  color: var(--nx-text-muted);
  margin-top: 1px;
  font-family: var(--nx-font-mono);
}
</style>
