import { useRef, useState } from 'react'
import { api } from '../api.js'

const STATUS_TEXT = {
  PARSING: '解析中',
  CHUNKING: '切片中',
  EMBEDDING: '向量化中',
  READY: '可检索',
  FAILED: '失败'
}

// 右侧/顶部知识库面板:上传文档 + 文档列表 + 状态/重建
export default function DocPanel({ documents, loading, onChanged }) {
  const fileRef = useRef(null)
  const [uploading, setUploading] = useState(false)
  const [reindexingId, setReindexingId] = useState(null)
  const [tip, setTip] = useState('')

  async function handleFile(e) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    setTip(`正在处理《${file.name}》:解析→切片→向量化,大文件请稍候…`)
    try {
      const doc = await api.uploadDocument(file)
      setTip(doc.status === 'FAILED' ? `处理失败:${doc.errorMsg || '未知错误'}` : `《${doc.title}》已入库,${doc.chunkCount} 个切片`)
      onChanged?.()
    } catch (err) {
      setTip('上传失败:' + err.message)
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function handleReindex(id) {
    setReindexingId(id)
    setTip('正在重建向量…')
    try {
      const doc = await api.reindexDocument(id)
      setTip(doc.status === 'FAILED' ? `重建失败:${doc.errorMsg}` : '向量重建完成')
      onChanged?.()
    } catch (err) {
      setTip('重建失败:' + err.message)
    } finally {
      setReindexingId(null)
    }
  }

  return (
    <section className="doc-panel">
      <div className="doc-head">
        <span>知识库文档({documents.length})</span>
        <button className="upload-btn" disabled={uploading} onClick={() => fileRef.current?.click()}>
          {uploading ? '处理中…' : '＋ 上传文档'}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".pdf,.doc,.docx,.txt,.md"
          hidden
          onChange={handleFile}
        />
      </div>
      {tip && <div className="doc-tip">{tip}</div>}

      <div className="doc-list">
        {loading && <div className="hint">加载中…</div>}
        {!loading && documents.length === 0 && (
          <div className="hint">知识库为空,先上传一份 PDF / Word / Markdown 资料</div>
        )}
        {documents.map((d) => (
          <div key={d.id} className="doc-item">
            <div className="doc-name" title={d.fileName}>{d.title}</div>
            <div className="doc-meta">
              <span className={`doc-status status-${d.status}`}>{STATUS_TEXT[d.status] || d.status}</span>
              <span>{d.chunkCount} 片</span>
              <span>{(d.fileSize / 1024).toFixed(0)} KB</span>
            </div>
            {d.status === 'FAILED' && (
              <div className="doc-error" title={d.errorMsg}>
                {d.errorMsg}
                <button disabled={reindexingId === d.id} onClick={() => handleReindex(d.id)}>
                  {reindexingId === d.id ? '重试中…' : '重试'}
                </button>
              </div>
            )}
            {(d.status === 'READY') && (
              <button className="reindex-link" disabled={reindexingId === d.id} onClick={() => handleReindex(d.id)}>
                {reindexingId === d.id ? '重建中…' : '重建向量'}
              </button>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}
