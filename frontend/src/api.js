// 智问后端接口封装。统一解包 ApiResponse{success,data,message,error}
const BASE = '/api/v1'

async function request(path, options = {}) {
  const resp = await fetch(BASE + path, options)
  const body = await resp.json().catch(() => null)
  if (!resp.ok || !body || body.success === false) {
    const msg = body?.message || body?.error?.message || `请求失败(${resp.status})`
    const err = new Error(msg)
    err.status = resp.status
    err.code = body?.error?.code
    throw err
  }
  return body.data
}

export const api = {
  // —— 会话 ——
  listConversations: () => request('/conversations'),
  createConversation: () =>
    request('/conversations', { method: 'POST' }),
  deleteConversation: (id) =>
    request(`/conversations/${id}`, { method: 'DELETE' }),
  getMessages: (id) => request(`/conversations/${id}/messages`),

  // —— 文档 ——
  listDocuments: () => request('/documents'),
  uploadDocument: (file, title) => {
    const fd = new FormData()
    fd.append('file', file)
    if (title) fd.append('title', title)
    return request('/documents/upload', { method: 'POST', body: fd })
  },
  reindexDocument: (id) =>
    request(`/documents/${id}/reindex`, { method: 'POST' })
}

/**
 * 订阅多轮 SSE 流式问答。
 * EventSource 只支持 GET,后端流式接口正好是 GET。
 *
 * @param {number|string} conversationId
 * @param {string} question
 * @param {{onSources:Function,onToken:Function,onDone:Function,onError:Function}} handlers
 * @returns {() => void} 取消函数
 */
export function streamChat(conversationId, question, handlers) {
  const es = new EventSource(
    `${BASE}/conversations/${conversationId}/chat/stream?q=${encodeURIComponent(question)}`
  )

  es.addEventListener('sources', (e) => {
    try {
      handlers.onSources?.(JSON.parse(e.data))
    } catch { /* 忽略坏帧 */ }
  })

  es.addEventListener('token', (e) => {
    try {
      handlers.onToken?.(JSON.parse(e.data))
    } catch { /* 忽略坏帧 */ }
  })

  es.addEventListener('done', (e) => {
    let meta = {}
    try { meta = JSON.parse(e.data) } catch { /* */ }
    handlers.onDone?.(meta)
    es.close()
  })

  es.addEventListener('error', (e) => {
    // 后端主动推的 error 帧 data 里是错误文案;连接级错误没有 data
    let msg = '连接中断,请重试'
    if (e.data) {
      try { msg = JSON.parse(e.data) } catch { msg = e.data }
    }
    handlers.onError?.(msg)
    es.close()
  })

  return () => es.close()
}
