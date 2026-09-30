import { useCallback, useEffect, useState } from 'react'
import { api } from './api.js'
import Sidebar from './components/Sidebar.jsx'
import ChatArea from './components/ChatArea.jsx'
import DocPanel from './components/DocPanel.jsx'

// 后端历史消息(ChatMessage)→ 前端展示消息;sources 字段是 JSON 字符串需解析
function toViewMessage(m) {
  let sources = []
  if (m.sources) {
    try { sources = JSON.parse(m.sources) } catch { sources = [] }
  }
  return {
    id: m.id,
    role: m.role,
    content: m.content,
    sources,
    fromKnowledge: m.fromKnowledge === 1,
    streaming: false
  }
}

export default function App() {
  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [messages, setMessages] = useState([])
  const [documents, setDocuments] = useState([])
  const [loadingConv, setLoadingConv] = useState(true)
  const [loadingDocs, setLoadingDocs] = useState(true)

  const loadConversations = useCallback(async () => {
    try {
      setConversations(await api.listConversations())
    } catch (e) {
      console.error('会话列表加载失败', e)
    } finally {
      setLoadingConv(false)
    }
  }, [])

  const loadDocuments = useCallback(async () => {
    try {
      setDocuments(await api.listDocuments())
    } catch (e) {
      console.error('文档列表加载失败', e)
    } finally {
      setLoadingDocs(false)
    }
  }, [])

  useEffect(() => { loadConversations(); loadDocuments() }, [loadConversations, loadDocuments])

  async function selectConversation(id) {
    setActiveId(id)
    try {
      const msgs = await api.getMessages(id)
      setMessages(msgs.map(toViewMessage))
    } catch (e) {
      setMessages([])
      alert('加载历史失败:' + e.message)
    }
  }

  async function createConversation() {
    try {
      const conv = await api.createConversation()
      await loadConversations()
      selectConversation(conv.id)
    } catch (e) {
      alert('创建会话失败:' + e.message)
    }
  }

  async function deleteConversation(id) {
    try {
      await api.deleteConversation(id)
      if (id === activeId) {
        setActiveId(null)
        setMessages([])
      }
      await loadConversations()
    } catch (e) {
      alert('删除失败:' + e.message)
    }
  }

  return (
    <div className="app">
      <Sidebar
        conversations={conversations}
        activeId={activeId}
        loading={loadingConv}
        onSelect={selectConversation}
        onCreate={createConversation}
        onDelete={deleteConversation}
      />
      <ChatArea
        conversationId={activeId}
        messages={messages}
        setMessages={setMessages}
      />
      <DocPanel
        documents={documents}
        loading={loadingDocs}
        onChanged={() => { loadDocuments(); loadConversations() }}
      />
    </div>
  )
}
