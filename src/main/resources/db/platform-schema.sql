CREATE TABLE IF NOT EXISTS platform_accounts (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    account_role VARCHAR(16) NOT NULL
);
CREATE TABLE IF NOT EXISTS platform_sessions (
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    title VARCHAR(120) NOT NULL,
    session_mode VARCHAR(16) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    PRIMARY KEY (user_id, session_id)
);
CREATE TABLE IF NOT EXISTS platform_runs (
    request_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    run_mode VARCHAR(16) NOT NULL,
    result_code VARCHAR(48) NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    detail_json TEXT NOT NULL,
    INDEX idx_platform_runs_user (user_id, created_at_ms)
);
CREATE TABLE IF NOT EXISTS platform_documents (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    content_text TEXT NOT NULL,
    document_version VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    chunk_count INT NOT NULL
);
CREATE TABLE IF NOT EXISTS platform_chunks (
    id VARCHAR(100) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    text_content TEXT NOT NULL,
    vector_json MEDIUMTEXT NOT NULL,
    CONSTRAINT fk_platform_chunk_document FOREIGN KEY (document_id) REFERENCES platform_documents(id)
);
