export function normalizeMcpResult(data) {
  const textBlocks = Array.isArray(data?.content)
    ? data.content
      .filter(block => block?.type === 'text' && typeof block.text === 'string')
      .map(block => block.text.trim())
      .filter(Boolean)
    : []

  if (textBlocks.length > 0) {
    return {
      content: textBlocks.join('\n\n'),
      markdown: true
    }
  }

  return {
    content: JSON.stringify(data, null, 2),
    markdown: false
  }
}

export function extractHttpLinks(text) {
  if (!text) return []

  const links = []
  const seen = new Set()
  const addLink = (url, label, isImage = false) => {
    const normalizedUrl = normalizeHttpUrl(url)
    if (!normalizedUrl || seen.has(normalizedUrl)) return
    seen.add(normalizedUrl)
    links.push({ url: normalizedUrl, label: label || '打开支付页面', isImage })
  }

  const markdownPattern = /(!?)\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)/g
  for (const match of text.matchAll(markdownPattern)) {
    addLink(match[3], match[2], match[1] === '!')
  }

  const bareUrlPattern = /https?:\/\/[^\s<>"']+/g
  for (const match of text.matchAll(bareUrlPattern)) {
    addLink(match[0].replace(/[),.;!?]+$/, ''), '打开支付页面')
  }

  return links
}

function normalizeHttpUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : ''
  } catch (_) {
    return ''
  }
}
