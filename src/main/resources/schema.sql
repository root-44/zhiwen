-- ============================================================
-- 智问 业务表结构(幂等:IF NOT EXISTS,启动时自动执行)
-- 向量表 vector_store 由 Spring AI PgVector 自动创建,这里只管业务元数据
-- ============================================================

-- 文档表:上传文档的元数据与处理状态
CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(500) NOT NULL,              -- 展示标题(默认取文件名去后缀)
    file_name    VARCHAR(500) NOT NULL,              -- 原始文件名
    file_type    VARCHAR(20)  NOT NULL,              -- pdf / docx / doc / md / txt
    file_size    BIGINT       NOT NULL,              -- 字节数
    char_count   INT          NOT NULL DEFAULT 0,    -- 解析出的总字符数
    chunk_count  INT          NOT NULL DEFAULT 0,    -- 切片总数
    status       VARCHAR(20)  NOT NULL DEFAULT 'PARSING',  -- PARSING/CHUNKING/READY/FAILED
    error_msg    TEXT,                               -- 失败原因
    deleted      SMALLINT     NOT NULL DEFAULT 0,    -- MyBatis-Plus 逻辑删除
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 切片表:文档切片元数据(向量本体存在 vector_store,通过 chunk_id 关联)
CREATE TABLE IF NOT EXISTS kb_chunk (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT      NOT NULL,
    seq              INT         NOT NULL,             -- 切片序号,从 0 开始
    content          TEXT        NOT NULL,             -- 切片文本
    char_count       INT         NOT NULL,             -- 字符数
    page_no          INT,                              -- PDF 页码(非 PDF 为 NULL)
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/DONE/FAILED
    vector_id        VARCHAR(64),                      -- vector_store 表主键,便于溯源/删除
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 常用查询索引
CREATE INDEX IF NOT EXISTS idx_chunk_document ON kb_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_document_status ON kb_document(status);

-- 会话表:一次多轮对话
CREATE TABLE IF NOT EXISTS kb_conversation (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL DEFAULT '新会话',  -- 默认取首轮问题前 20 字
    message_count INT       NOT NULL DEFAULT 0,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 消息表:会话内每一轮 user/assistant 消息
CREATE TABLE IF NOT EXISTS kb_message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,              -- user / assistant
    content         TEXT        NOT NULL,
    sources         TEXT,                             -- assistant 消息的引用来源 JSON 数组
    from_knowledge  SMALLINT,                          -- assistant:是否命中知识库
    seq             INT         NOT NULL,              -- 会话内消息序号
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_conversation ON kb_message(conversation_id, seq);
