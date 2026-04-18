import StartNode from './components/nodes/StartNode.vue'
import EndNode from './components/nodes/EndNode.vue'
import AgentNode from './components/nodes/AgentNode.vue'
import ToolNode from './components/nodes/ToolNode.vue'
import ConditionNode from './components/nodes/ConditionNode.vue'
import ParallelNode from './components/nodes/ParallelNode.vue'
import HttpNode from './components/nodes/HttpNode.vue'
import KnowledgeNode from './components/nodes/KnowledgeNode.vue'
import CodeNode from './components/nodes/CodeNode.vue'
import HumanReviewNode from './components/nodes/HumanReviewNode.vue'

export const nodeTypes = {
  start: StartNode,
  end: EndNode,
  agent: AgentNode,
  tool: ToolNode,
  condition: ConditionNode,
  parallel: ParallelNode,
  http: HttpNode,
  knowledge: KnowledgeNode,
  code: CodeNode,
  human_review: HumanReviewNode,
}

export const nodeDefinitions = [
  { type: 'start',        label: '开始',     icon: '▶',  color: '#52c41a', description: '工作流入口' },
  { type: 'end',          label: '结束',     icon: '⏹',  color: '#ff4d4f', description: '工作流终点' },
  { type: 'agent',        label: 'Agent',    icon: '🤖', color: '#1890ff', description: '调用AI助手' },
  { type: 'tool',         label: '工具',     icon: '🔧', color: '#faad14', description: '调用注册工具' },
  { type: 'condition',    label: '条件分支', icon: '❓', color: '#722ed1', description: 'SpEL条件判断' },
  { type: 'parallel',     label: '并行',     icon: '⚡', color: '#13c2c2', description: '并行执行多分支' },
  { type: 'http',         label: 'HTTP',     icon: '🌐', color: '#eb2f96', description: '发送HTTP请求' },
  { type: 'knowledge',    label: '知识库',   icon: '📚', color: '#52c41a', description: '向量检索知识库' },
  { type: 'code',         label: '代码',     icon: '💻', color: '#fa8c16', description: '执行JS代码' },
  { type: 'human_review', label: '人工审批', icon: '👤', color: '#f5222d', description: '暂停等待审批' },
]
