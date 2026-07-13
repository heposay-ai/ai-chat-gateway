<template>
  <!-- 分享页面：独立布局 -->
  <template v-if="route.meta.layout === 'blank'">
    <router-view />
  </template>

  <!-- 管理后台：NEXUS COMMAND 布局 -->
  <el-container class="nx-layout" v-else>
    <el-aside width="220px" class="nx-sidebar">
      <div class="nx-logo">
        <span class="nx-logo-text">NEXUS</span>
        <span class="nx-logo-sep">//</span>
        <span class="nx-logo-cmd">CMD</span>
      </div>

      <el-menu :default-active="activeRoute" router class="nx-nav">
        <el-menu-item index="/dashboard">
          <span class="nx-nav-prefix">[DAS]</span>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/keys">
          <span class="nx-nav-prefix">[KEY]</span>
          <span>Key 管理</span>
        </el-menu-item>
        <el-menu-item index="/templates">
          <span class="nx-nav-prefix">[TPL]</span>
          <span>模板管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <span class="nx-nav-prefix">[CHT]</span>
          <span>对话测试</span>
        </el-menu-item>
        <el-menu-item index="/knowledge-list">
          <span class="nx-nav-prefix">[KBS]</span>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <span class="nx-nav-prefix">[RAG]</span>
          <span>RAG 测试</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <span class="nx-nav-prefix">[AGT]</span>
          <span>Agent 管理</span>
        </el-menu-item>
        <el-menu-item index="/workflows">
          <span class="nx-nav-prefix">[WKF]</span>
          <span>工作流</span>
        </el-menu-item>
        <el-menu-item index="/mcp-test">
          <span class="nx-nav-prefix">[MCP]</span>
          <span>MCP 测试</span>
        </el-menu-item>
        <el-sub-menu index="/proxy">
          <template #title>
            <span class="nx-nav-prefix">[PXY]</span>
            <span>代理池管理</span>
          </template>
          <el-menu-item index="/proxy/dashboard">
            <span class="nx-nav-prefix">[DAS]</span>
            <span>数据看板</span>
          </el-menu-item>
          <el-menu-item index="/proxy/ips">
            <span class="nx-nav-prefix">[IPS]</span>
            <span>IP 管理</span>
          </el-menu-item>
          <el-menu-item index="/proxy/scripts">
            <span class="nx-nav-prefix">[SCN]</span>
            <span>扫描脚本</span>
          </el-menu-item>
          <el-menu-item index="/proxy/accounts">
            <span class="nx-nav-prefix">[ACT]</span>
            <span>账号池</span>
          </el-menu-item>
          <el-menu-item index="/proxy/gateway">
            <span class="nx-nav-prefix">[GW]</span>
            <span>代理网关</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="nx-sidebar-footer">
        <div class="nx-theme-toggle" @click="toggleTheme">
          <span class="nx-theme-icon">{{ isDark ? '◑' : '◐' }}</span>
          <span>{{ isDark ? '白天模式' : '暗夜模式' }}</span>
        </div>
        <div class="nx-key-label">> API_KEY</div>
        <el-input v-model="apiKey" placeholder="sk-..." size="small"
          @change="saveApiKey" type="password" show-password />
        <div class="nx-status">
          <span class="nx-status-dot" :class="{ active: apiKey }"></span>
          <span>{{ apiKey ? '已连接' : '未设置' }}</span>
        </div>
      </div>
    </el-aside>

    <el-main class="nx-main nx-grid-bg">
      <router-view v-slot="{ Component }">
        <transition name="nx-page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, computed, provide } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const apiKey = ref(localStorage.getItem('apiKey') || '')

// 主题切换
const isDark = ref((localStorage.getItem('nx-theme') || 'dark') === 'dark')
provide('isDark', isDark)

function toggleTheme() {
  isDark.value = !isDark.value
  const theme = isDark.value ? 'dark' : 'light'
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('nx-theme', theme)
  // 通知 ECharts 等组件重建
  window.dispatchEvent(new CustomEvent('nx-theme-change', { detail: theme }))
}

// 处理子路由激活态
const activeRoute = computed(() => {
  const p = route.path
  if (p.startsWith('/knowledge/') || p === '/knowledge-list') return '/knowledge-list'
  if (p.startsWith('/agents/') || p === '/agents') return '/agents'
  if (p.startsWith('/workflows')) return '/workflows'
  if (p.startsWith('/proxy/')) return p
  return p
})

function saveApiKey() {
  localStorage.setItem('apiKey', apiKey.value)
}
</script>

<style>
/* 页面切换动画 */
.nx-page-enter-active,
.nx-page-leave-active {
  transition: all 200ms ease-out;
}
.nx-page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.nx-page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>

<style scoped>
.nx-layout {
  height: 100vh;
  background: var(--nx-bg-deep);
}

/* ── Sidebar ── */
.nx-sidebar {
  background: var(--nx-bg-surface);
  border-right: 1px solid var(--nx-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nx-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-bottom: 1px solid var(--nx-border);
  position: relative;
}
.nx-logo::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 40px;
  height: 2px;
  background: var(--nx-accent-amber);
}
.nx-logo-text {
  font-family: var(--nx-font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--nx-text-primary);
  letter-spacing: 3px;
}
.nx-logo-sep {
  font-family: var(--nx-font-mono);
  color: var(--nx-accent-amber);
  font-size: 14px;
}
.nx-logo-cmd {
  font-family: var(--nx-font-mono);
  font-size: 14px;
  font-weight: 500;
  color: var(--nx-accent-amber);
  letter-spacing: 2px;
}

.nx-nav {
  flex: 1;
  padding: 8px 0;
}

.nx-nav-prefix {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  margin-right: 8px;
  width: 34px;
  display: inline-block;
}
.nx-nav .el-menu-item.is-active .nx-nav-prefix {
  color: var(--nx-accent-amber);
}

/* ── Sidebar Footer ── */
.nx-sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--nx-border);
}

.nx-theme-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 10px;
  border-radius: 2px;
  cursor: pointer;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  letter-spacing: 1px;
  text-transform: uppercase;
  transition: all 150ms;
  border: 1px solid transparent;
}
.nx-theme-toggle:hover {
  color: var(--nx-accent-amber);
  border-color: var(--nx-border);
  background: var(--nx-bg-raised);
}
.nx-theme-icon {
  font-size: 14px;
  line-height: 1;
}

.nx-key-label {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-accent-amber);
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.nx-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.nx-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nx-accent-rose);
}
.nx-status-dot.active {
  background: var(--nx-accent-teal);
  box-shadow: 0 0 6px var(--nx-accent-teal);
}

/* ── Main Content ── */
.nx-main {
  padding: 24px;
  overflow: auto;
}
</style>
