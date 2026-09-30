import { useEffect, useRef, useState } from 'react'
import { streamChat } from '../api.js'
import Sources from './Sources.jsx'

// 中间对话区:消息流 + SSE 打字机 + 输入框
export default function ChatArea({ conversationId, messages, setMessages }) {
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const scrollRef = useRef(null)
  const cancelRef = useRef(null)

  // 消息变化时滚动到底部
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages])

  // 离开会话时取消还在进行的流
  useEffect(() => () => cancelRef.current?.(), [conversationId])

  async function send() {
    const question = input.trim()
    if (!question || sending || !conversationId) return

    const userMsg = { id: `u-${Date.now()}`, role: 'user', content: question }
    // assistant 占位:等待 sources/token 流式填充
    const aiId = `a-${Date.now()}`
    const aiMsg = { id: aiId, role: 'assistant', content: '', sources: [], streaming: true }
    setMessages((m) => [...m, userMsg, aiMsg])
    setInput('')
    setSending(true)

    const patchAi = (patch) =>
      setMessages((m) => m.map((x) => (x.id === aiId ? { ...x, ...patch } : x)))

    cancelRef.current = streamChat(conversationId, question, {
      onSources: (sources) => patchAi({ sources }),
      onToken: (delta) =>
        setMessages((m) =>
          m.map((x) => (x.id === aiId ? { ...x, content: x.content + delta } : x))
        ),
      onDone: () => {
        patchAi({ streaming: false })
        setSending(false)
        cancelRef.current = null
      },
      onError: (msg) => {
        patchAi({ streaming: false, error: msg, content: msg })
        setSending(false)
        cancelRef.current = null
      }
    })
  }

  function onKeyDown(e) {
    // Enter 发送,Shift+Enter 换行
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  return (
    <section className="chat-area">
      <div className="messages" ref={scrollRef}>
        {messages.length === 0 && (
          <div className="empty-chat">
            <div className="empty-logo">智</div>
            <h2>向你的知识库提问</h2>
            <p>例如:稻瘟病用什么药?什么时期施药?</p>
            <p className="empty-sub">回答仅依据已上传文档,并标注引用来源;没有依据时会如实说明。</p>
          </div>
        )}

        {messages.map((m) => (
          <div key={m.id} className={`msg msg-${m.role}`}>
            <div className="msg-avatar">{m.role === 'user' ? '我' : '智'}</div>
            <div className="msg-body">
              <div className="msg-content">
                {m.content}
                {m.streaming && <span className="cursor" />}
              </div>
              {m.error && <div className="msg-error">⚠ {m.error}</div>}
              {m.sources?.length > 0 && <Sources sources={m.sources} />}
            </div>
          </div>
        ))}
      </div>

      <div className="composer">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={conversationId ? '输入问题,Enter 发送 / Shift+Enter 换行' : '请先新建或选择一个会话'}
          rows={1}
          disabled={!conversationId}
        />
        <button onClick={send} disabled={sending || !input.trim() || !conversationId}>
          {sending ? '生成中…' : '发送'}
        </button>
      </div>
    </section>
  )
}
