-- SACHET AI schema v2 (Phases 2–3). Portable PostgreSQL + H2. Idempotent.
-- Full table reference: docs/DATABASE_SCHEMA.md.

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  clearance VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  department VARCHAR(128),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(128) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id VARCHAR(36) NOT NULL REFERENCES users(id),
  role_id BIGINT NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL REFERENCES roles(id),
  permission_id BIGINT NOT NULL REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS documents (
  id VARCHAR(36) PRIMARY KEY,
  title VARCHAR(512) NOT NULL,
  owner_id VARCHAR(36) NOT NULL,
  department VARCHAR(128),
  classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  status VARCHAR(32) NOT NULL DEFAULT 'CURRENT',
  version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS document_permissions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL REFERENCES documents(id),
  principal_type VARCHAR(16) NOT NULL,
  principal_id VARCHAR(128) NOT NULL,
  can_read BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (document_id, principal_type, principal_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor VARCHAR(36) NOT NULL,
  action VARCHAR(64) NOT NULL,
  doc_ids VARCHAR(4000) NOT NULL DEFAULT '',
  classification VARCHAR(32),
  pages VARCHAR(512) NOT NULL DEFAULT '',
  model VARCHAR(128) NOT NULL DEFAULT '',
  prev_hash VARCHAR(64) NOT NULL,
  entry_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

-- Phase 3 additions (idempotent for existing Phase 2 databases).
ALTER TABLE documents ADD COLUMN IF NOT EXISTS family_id VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS sha256 VARCHAR(64);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS source_filename VARCHAR(512);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS page_count INT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS chunk_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ingestion_jobs (
  id VARCHAR(36) PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL REFERENCES documents(id),
  status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  progress INT NOT NULL DEFAULT 0,
  error_code VARCHAR(32),
  error_message VARCHAR(512),
  blob_key VARCHAR(512),
  created_by VARCHAR(36) NOT NULL,
  attempts INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS document_chunks (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL REFERENCES documents(id),
  chunk_index INT NOT NULL,
  page_number INT NOT NULL,
  section VARCHAR(512),
  text VARCHAR(20000) NOT NULL,
  char_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);

-- Phase 4: portable embedding column (VARCHAR holds the float vector; no native
-- vector type required, so PostgreSQL and H2 share one schema).
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS embedding VARCHAR(8000);

-- Phase 6: generated studio artifacts (DOCX/PPTX). Bytes in storage; metadata here.
CREATE TABLE IF NOT EXISTS generated_artifacts (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  title VARCHAR(512) NOT NULL,
  type VARCHAR(16) NOT NULL,
  classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  source_doc_ids VARCHAR(4000) NOT NULL DEFAULT '',
  blob_key VARCHAR(512) NOT NULL,
  mime_type VARCHAR(128),
  size BIGINT NOT NULL DEFAULT 0,
  sha256 VARCHAR(64),
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_artifacts_owner ON generated_artifacts(owner_id);
