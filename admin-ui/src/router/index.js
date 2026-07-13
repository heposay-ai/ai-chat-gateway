import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板' } },
  { path: '/keys', component: () => import('../views/KeyManage.vue'), meta: { title: 'Key 管理' } },
  { path: '/templates', component: () => import('../views/TemplateManage.vue'), meta: { title: '模板管理' } },
  { path: '/chat', component: () => import('../views/ChatTest.vue'), meta: { title: '对话测试' } },
  // W3 原知识库页面（向后兼容）
  { path: '/knowledge', component: () => import('../views/KnowledgeBase.vue'), meta: { title: 'RAG 测试' } },
  // W4 知识库管理
  { path: '/knowledge-list', component: () => import('../views/KnowledgeBaseList.vue'), meta: { title: '知识库管理' } },
  { path: '/knowledge/:id', component: () => import('../views/KnowledgeBaseDetail.vue'), meta: { title: '知识库详情' } },
  { path: '/knowledge/:id/chat', component: () => import('../views/RagChat.vue'), meta: { title: 'RAG 问答' } },
  { path: '/knowledge/:id/debug', component: () => import('../views/RagDebug.vue'), meta: { title: 'RAG Debug' } },
  // W5 Agent
  { path: '/agents', component: () => import('../views/AgentList.vue'), meta: { title: 'Agent 管理' } },
  { path: '/agents/:id/chat', component: () => import('../views/AgentChat.vue'), meta: { title: 'Agent 对话' } },
  // W6 工作流引擎
  { path: '/workflows', component: () => import('../views/workflow/WorkflowList.vue'), meta: { title: '工作流管理' } },
  { path: '/workflows/new', component: () => import('../views/workflow/WorkflowEditor.vue'), meta: { title: '创建工作流' } },
  { path: '/workflows/:id', component: () => import('../views/workflow/WorkflowEditor.vue'), meta: { title: '编辑工作流' } },
  { path: '/workflows/:id/execution', component: () => import('../views/workflow/WorkflowExecution.vue'), meta: { title: '执行监控' } },
  { path: '/mcp-test', component: () => import('../views/McpTest.vue'), meta: { title: 'MCP 测试' } },
  // 代理池子系统
  { path: '/proxy/dashboard', component: () => import('../views/proxy/ProxyDashboard.vue'), meta: { title: '代理池看板' } },
  { path: '/proxy/ips', component: () => import('../views/proxy/ProxyIpList.vue'), meta: { title: 'IP 管理' } },
  { path: '/proxy/scripts', component: () => import('../views/proxy/ScanScriptList.vue'), meta: { title: '扫描脚本' } },
  { path: '/proxy/accounts', component: () => import('../views/proxy/ProxyAccountList.vue'), meta: { title: '账号池' } },
  { path: '/proxy/gateway', component: () => import('../views/proxy/ProxyGateway.vue'), meta: { title: '代理网关' } },
  // W4 分享链接（独立页面，无侧边栏）
  { path: '/share/:token', component: () => import('../views/SharedChat.vue'), meta: { title: '知识库问答', layout: 'blank' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = (to.meta.title || 'AI Chat Gateway') + ' - 管理后台'
})

export default router
