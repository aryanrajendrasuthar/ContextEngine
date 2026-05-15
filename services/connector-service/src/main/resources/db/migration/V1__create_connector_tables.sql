
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE connector_type AS ENUM ('GITHUB', 'SLACK', 'JIRA', 'CONFLUENCE', 'WEBHOOK');
CREATE TYPE connector_status AS ENUM ('ACTIVE', 'INACTIVE', 'ERROR', 'SYNCING');

CREATE TABLE connectors (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    VARCHAR(255) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    connector_type     VARCHAR(50)  NOT NULL,
    config             JSONB        NOT NULL DEFAULT '{}',
    status             VARCHAR(50)  NOT NULL DEFAULT 'INACTIVE',
    last_sync_at       TIMESTAMPTZ,
    last_sync_status   VARCHAR(50),
    error_message      TEXT,
    documents_indexed  BIGINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conn_org_id   ON connectors (organization_id);
CREATE INDEX idx_conn_status   ON connectors (status);
CREATE INDEX idx_conn_type     ON connectors (connector_type);

COMMENT ON TABLE connectors IS 'Connector configuration records — one row per data source integration';
COMMENT ON COLUMN connectors.config IS 'Source-specific configuration, e.g. API URL, token, repository name. Stored as JSONB to support heterogeneous connector types without schema changes.';
COMMENT ON COLUMN connectors.documents_indexed IS 'Running total of events successfully forwarded to the ingestion service across all sync runs.';
