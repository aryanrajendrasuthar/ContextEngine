
CREATE TABLE query_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id VARCHAR(255) NOT NULL,
    question        TEXT        NOT NULL,
    answer          TEXT,
    source_count    INTEGER     NOT NULL DEFAULT 0,
    confidence      NUMERIC(4, 3),
    cache_hit       BOOLEAN     NOT NULL DEFAULT FALSE,
    duration_ms     INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_qh_org_created ON query_history (organization_id, created_at DESC);

COMMENT ON TABLE query_history IS 'Log of every query answered by the RAG pipeline, used for analytics and debugging.';
COMMENT ON COLUMN query_history.confidence IS 'Average cosine similarity of the top retrieved chunks, 0.000–1.000.';
COMMENT ON COLUMN query_history.cache_hit IS 'True if this response was served from the Redis query cache.';
