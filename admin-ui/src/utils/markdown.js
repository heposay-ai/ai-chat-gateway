import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: false,        // 禁用 HTML 标签（防 XSS）
  linkify: true,      // 自动识别链接
  typographer: false,
  highlight(str, lang) {
    const copyButton = '<button class="nx-code-copy" type="button" title="复制代码" onclick="window.nxCopyCodeBlock && window.nxCopyCodeBlock(this)">复制</button>'
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<div class="nx-code-block">${copyButton}<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre></div>`
      } catch (_) { /* fallback */ }
    }
    return `<div class="nx-code-block">${copyButton}<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre></div>`
  }
})

if (typeof window !== 'undefined' && !window.nxCopyCodeBlock) {
  window.nxCopyCodeBlock = async (button) => {
    const code = button?.parentElement?.querySelector('code')?.innerText || ''
    if (!code) return
    try {
      await navigator.clipboard.writeText(code)
      const originalText = button.textContent
      button.textContent = '已复制'
      button.classList.add('is-copied')
      window.setTimeout(() => {
        button.textContent = originalText
        button.classList.remove('is-copied')
      }, 1200)
    } catch (_) {
      button.textContent = '复制失败'
      window.setTimeout(() => { button.textContent = '复制' }, 1200)
    }
  }
}

/**
 * 渲染 Markdown 为 HTML
 * @param {string} text - Markdown 文本
 * @returns {string} 安全的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}

/**
 * 渲染行内 Markdown（不包裹 <p>）
 */
export function renderInline(text) {
  if (!text) return ''
  return md.renderInline(text)
}

export default md
