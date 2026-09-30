// 左侧栏:Logo + 新建会话 + 会话列表 + 删除
export default function Sidebar({ conversations, activeId, loading, onSelect, onCreate, onDelete }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-logo">智</span>
        <div>
          <div className="brand-name">智问</div>
          <div className="brand-sub">农业知识库问答</div>
        </div>
      </div>

      <button className="new-chat-btn" onClick={onCreate}>＋ 新建会话</button>

      <div className="conv-list">
        {loading && <div className="hint">加载中…</div>}
        {!loading && conversations.length === 0 && (
          <div className="hint">还没有会话,点击上方按钮开始</div>
        )}
        {conversations.map((c) => (
          <div
            key={c.id}
            className={`conv-item ${c.id === activeId ? 'active' : ''}`}
            onClick={() => onSelect(c.id)}
          >
            <span className="conv-title">{c.title}</span>
            <button
              className="conv-del"
              title="删除会话"
              onClick={(e) => {
                e.stopPropagation()
                if (confirm(`删除会话「${c.title}」?`)) onDelete(c.id)
              }}
            >
              ×
            </button>
          </div>
        ))}
      </div>

      <div className="sidebar-foot">RAG · Spring AI · PgVector</div>
    </aside>
  )
}
