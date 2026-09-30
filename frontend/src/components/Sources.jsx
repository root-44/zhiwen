// 引用来源:把 RAG 命中的切片渲染成可展开的卡片,体现"回答可溯源"
export default function Sources({ sources }) {
  if (!sources || sources.length === 0) return null
  return (
    <div className="sources">
      <div className="sources-title">参考来源 · {sources.length}</div>
      {sources.map((s, i) => (
        <details key={s.chunkId ?? i} className="source-item">
          <summary>
            <span className="source-idx">[{i + 1}]</span>
            <span className="source-name">{s.title || '未命名文档'}</span>
            {s.pageNo != null && <span className="source-page">第 {s.pageNo} 页</span>}
            <span className="source-score">相似度 {s.score?.toFixed?.(3) ?? s.score}</span>
          </summary>
          <p className="source-content">{s.content}</p>
        </details>
      ))}
    </div>
  )
}
