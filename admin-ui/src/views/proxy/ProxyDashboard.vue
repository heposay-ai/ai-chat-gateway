<template>
  <div class="nx-page">
    <div class="nx-page-header">
      <h2 class="nx-title">代理池看板</h2>
      <div class="nx-actions">
        <el-radio-group v-model="days" @change="loadAll" size="small">
          <el-radio-button :value="1">今天</el-radio-button>
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 概览卡片 -->
    <div class="nx-stat-cards">
      <div class="nx-stat-card">
        <div class="nx-stat-label">总账号数</div>
        <div class="nx-stat-value">{{ overview.totalAccounts || 0 }}</div>
      </div>
      <div class="nx-stat-card">
        <div class="nx-stat-label">健康账号</div>
        <div class="nx-stat-value nx-stat-ok">{{ overview.healthyAccounts || 0 }}</div>
      </div>
      <div class="nx-stat-card">
        <div class="nx-stat-label">总请求数</div>
        <div class="nx-stat-value">{{ overview.totalRequests || 0 }}</div>
      </div>
      <div class="nx-stat-card">
        <div class="nx-stat-label">总 Token</div>
        <div class="nx-stat-value">{{ formatNumber(overview.totalTokens || 0) }}</div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="nx-chart-row">
      <div class="nx-chart-box">
        <h3 class="nx-chart-title">Token 使用趋势</h3>
        <div ref="tokenChartRef" class="nx-chart"></div>
      </div>
      <div class="nx-chart-box">
        <h3 class="nx-chart-title">费用趋势</h3>
        <div ref="costChartRef" class="nx-chart"></div>
      </div>
    </div>

    <!-- 分布图 -->
    <div class="nx-chart-row">
      <div class="nx-chart-box">
        <h3 class="nx-chart-title">模型使用分布</h3>
        <div ref="modelChartRef" class="nx-chart"></div>
      </div>
      <div class="nx-chart-box">
        <h3 class="nx-chart-title">账号使用排行</h3>
        <div ref="accountChartRef" class="nx-chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, inject } from 'vue'
import * as echarts from 'echarts'
import { getDashboardOverview, getTokenTrend, getCostTrend, getModelDistribution, getAccountRanking } from '../../api/proxy'

const isDark = inject('isDark', ref(true))
const days = ref(7)
const overview = ref({})

const tokenChartRef = ref(null)
const costChartRef = ref(null)
const modelChartRef = ref(null)
const accountChartRef = ref(null)

let tokenChart, costChart, modelChart, accountChart

function formatNumber(n) {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return n
}

function getThemeColors() {
  return isDark.value
    ? { bg: 'transparent', text: '#a0a0a0', line: '#2a2a2a', accent: '#f5a623' }
    : { bg: 'transparent', text: '#666', line: '#e0e0e0', accent: '#1890ff' }
}

async function loadOverview() {
  try { overview.value = await getDashboardOverview() } catch { /* empty */ }
}

async function loadTokenTrend() {
  try {
    const data = await getTokenTrend(days.value)
    const c = getThemeColors()
    tokenChart?.setOption({
      grid: { top: 20, right: 20, bottom: 30, left: 60 },
      xAxis: { type: 'category', data: data.map(d => d.date), axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text } },
      yAxis: { type: 'value', axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.line } } },
      tooltip: { trigger: 'axis' },
      series: [{ type: 'line', data: data.map(d => d.tokens), smooth: true, areaStyle: { opacity: 0.15 }, lineStyle: { color: c.accent }, itemStyle: { color: c.accent } }]
    })
  } catch { /* empty */ }
}

async function loadCostTrend() {
  try {
    const data = await getCostTrend(days.value)
    const c = getThemeColors()
    costChart?.setOption({
      grid: { top: 20, right: 20, bottom: 30, left: 60 },
      xAxis: { type: 'category', data: data.map(d => d.date), axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text } },
      yAxis: { type: 'value', axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text, formatter: '${value}' }, splitLine: { lineStyle: { color: c.line } } },
      tooltip: { trigger: 'axis', valueFormatter: v => '$' + (v || 0).toFixed(4) },
      series: [{ type: 'bar', data: data.map(d => d.cost), itemStyle: { color: '#52c41a' } }]
    })
  } catch { /* empty */ }
}

async function loadModelDist() {
  try {
    const data = await getModelDistribution(days.value)
    modelChart?.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['40%', '70%'], center: ['50%', '55%'],
        data: data.map(d => ({ name: d.model, value: d.count })),
        label: { color: getThemeColors().text, fontSize: 11 }
      }]
    })
  } catch { /* empty */ }
}

async function loadAccountRank() {
  try {
    const data = await getAccountRanking(days.value, 10)
    const c = getThemeColors()
    accountChart?.setOption({
      grid: { top: 10, right: 20, bottom: 30, left: 120 },
      xAxis: { type: 'value', axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.line } } },
      yAxis: { type: 'category', data: data.map(d => d.name || `#${d.accountId}`).reverse(), axisLine: { lineStyle: { color: c.line } }, axisLabel: { color: c.text, fontSize: 11 } },
      tooltip: { trigger: 'axis' },
      series: [{ type: 'bar', data: data.map(d => d.requests).reverse(), itemStyle: { color: c.accent } }]
    })
  } catch { /* empty */ }
}

function loadAll() {
  loadOverview()
  loadTokenTrend()
  loadCostTrend()
  loadModelDist()
  loadAccountRank()
}

function initCharts() {
  if (tokenChartRef.value) tokenChart = echarts.init(tokenChartRef.value)
  if (costChartRef.value) costChart = echarts.init(costChartRef.value)
  if (modelChartRef.value) modelChart = echarts.init(modelChartRef.value)
  if (accountChartRef.value) accountChart = echarts.init(accountChartRef.value)
}

function handleResize() {
  tokenChart?.resize()
  costChart?.resize()
  modelChart?.resize()
  accountChart?.resize()
}

function handleThemeChange() {
  loadAll()
}

onMounted(async () => {
  await nextTick()
  initCharts()
  loadAll()
  window.addEventListener('resize', handleResize)
  window.addEventListener('nx-theme-change', handleThemeChange)
})

onUnmounted(() => {
  tokenChart?.dispose()
  costChart?.dispose()
  modelChart?.dispose()
  accountChart?.dispose()
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('nx-theme-change', handleThemeChange)
})
</script>

<style scoped>
.nx-page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.nx-title { font-family: var(--nx-font-mono); font-size: 18px; color: var(--nx-text-primary); }

.nx-stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.nx-stat-card {
  background: var(--nx-bg-surface); border: 1px solid var(--nx-border); border-radius: 4px; padding: 20px;
}
.nx-stat-label { font-family: var(--nx-font-mono); font-size: 11px; color: var(--nx-text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 8px; }
.nx-stat-value { font-family: var(--nx-font-mono); font-size: 28px; font-weight: 600; color: var(--nx-text-primary); }
.nx-stat-ok { color: var(--nx-accent-teal); }

.nx-chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.nx-chart-box {
  background: var(--nx-bg-surface); border: 1px solid var(--nx-border); border-radius: 4px; padding: 16px;
}
.nx-chart-title { font-family: var(--nx-font-mono); font-size: 13px; color: var(--nx-text-primary); margin: 0 0 8px; }
.nx-chart { width: 100%; height: 280px; }
</style>
