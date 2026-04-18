<template>
  <div class="nx-flow-canvas" @drop="onDrop" @dragover.prevent>
    <VueFlow
      v-model:nodes="localNodes"
      v-model:edges="localEdges"
      :node-types="nodeTypes"
      :default-edge-options="{ animated: true, type: 'smoothstep' }"
      @connect="onConnect"
      @node-click="onNodeClick"
      @pane-click="onPaneClick"
      @edge-click="onEdgeClick"
      fit-view-on-init
      class="vue-flow-wrapper"
    >
      <Background :gap="20" />
      <Controls />
      <MiniMap />
    </VueFlow>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { nodeTypes } from '../nodeTypes'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import '@vue-flow/controls/dist/style.css'

const props = defineProps({
  nodes: { type: Array, required: true },
  edges: { type: Array, required: true },
})

const emit = defineEmits(['update:nodes', 'update:edges', 'node-select', 'node-deselect', 'edge-remove'])

const localNodes = computed({
  get: () => props.nodes,
  set: (val) => emit('update:nodes', val),
})
const localEdges = computed({
  get: () => props.edges,
  set: (val) => emit('update:edges', val),
})

const { project } = useVueFlow()

let nodeCounter = 100

const onDrop = (event) => {
  const raw = event.dataTransfer.getData('application/workflow-node')
  if (!raw) return

  const nodeDef = JSON.parse(raw)
  const bounds = event.currentTarget.getBoundingClientRect()
  const position = project({
    x: event.clientX - bounds.left,
    y: event.clientY - bounds.top,
  })

  nodeCounter++
  const newNode = {
    id: `${nodeDef.type}_${nodeCounter}`,
    type: nodeDef.type,
    position,
    data: {
      label: nodeDef.label,
      nodeType: nodeDef.type.toUpperCase(),
    },
  }
  emit('update:nodes', [...props.nodes, newNode])
}

const onConnect = (params) => {
  const id = `e-${params.source}-${params.target}`
  if (props.edges.some(e => e.id === id)) return
  emit('update:edges', [...props.edges, {
    id,
    source: params.source,
    sourceHandle: params.sourceHandle,
    target: params.target,
    targetHandle: params.targetHandle,
    animated: true,
    type: 'smoothstep',
  }])
}

const onNodeClick = ({ node }) => {
  emit('node-select', node)
}

const onPaneClick = () => {
  emit('node-deselect')
}

const onEdgeClick = ({ edge }) => {
  emit('edge-remove', edge.id)
}
</script>

<style scoped>
.nx-flow-canvas {
  flex: 1;
  height: 100%;
  position: relative;
}

.vue-flow-wrapper {
  width: 100%;
  height: 100%;
}
</style>

<style>
/* vue-flow 全局覆写，适配 NEXUS 主题 */
.vue-flow__background {
  background-color: var(--nx-bg-deep) !important;
}
.vue-flow__minimap {
  background: var(--nx-bg-surface) !important;
  border: 1px solid var(--nx-border) !important;
  border-radius: 4px;
}
.vue-flow__controls {
  background: var(--nx-bg-surface) !important;
  border: 1px solid var(--nx-border) !important;
  border-radius: 4px;
}
.vue-flow__controls-button {
  background: var(--nx-bg-surface) !important;
  border-bottom: 1px solid var(--nx-border) !important;
  fill: var(--nx-text-secondary) !important;
}
.vue-flow__controls-button:hover {
  background: var(--nx-bg-raised) !important;
}
.vue-flow__edge-path {
  stroke: var(--nx-text-muted) !important;
}
.vue-flow__edge.animated .vue-flow__edge-path {
  stroke: var(--nx-accent-teal) !important;
}
</style>
