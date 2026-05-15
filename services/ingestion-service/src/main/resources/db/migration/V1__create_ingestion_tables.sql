
-- Ingestion service schema
-- All tables owned by the ingestion-service. Other services must not write to these tables.

CREATE TABLE IF NOT EXISTS knowledge_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id VARCHAR(255) NOT NULL,
    source_id       VARCHAR(500) NOT NULL,
    source_type     VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    author_id       VARCHAR(255),
    author_name     VARCHAR(255),
    timestamp       TIMESTAMPTZ  NOT NULL,
    url             VARCHAR(2000),
    metadata        JSONB,
    status          VARCHAR(50)  NOT NULL DEFAULT 'RECEIVED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Unique constraint ensures idempotent ingestion: same source event cannot be stored twice
CREATE UNIQUE INDEX IF NOT EXISTS idx_ke_org_source_id
    ON knowledge_events (organization_id, source_id);

CREATE INDEX IF NOT EXISTS idx_ke_content_hash
    ON knowledge_events (content_hash);

CREATE INDEX IF NOT EXISTS idx_ke_status
    ON knowledge_events (status);

CREATE INDEX IF NOT EXISTS idx_ke_timestamp
    ON knowledge_events (timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_ke_org_source_type
    ON knowledge_events (organization_id, source_type);

-- Ingestion jobs track scheduled connector runs for audit and retry
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id VARCHAR(255) NOT NULL,
    connector_id    UUID         NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'RUNNING',
    events_received INTEGER      NOT NULL DEFAULT 0,
    events_accepted INTEGER      NOT NULL DEFAULT 0,
    events_duplicate INTEGER     NOT NULL DEFAULT 0,
    events_failed   INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ij_connector_id
    ON ingestion_jobs (connector_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ij_org_status
    ON ingestion_jobs (organization_id, status);
