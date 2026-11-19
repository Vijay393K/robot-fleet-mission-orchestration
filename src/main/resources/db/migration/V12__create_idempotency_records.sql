CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,

    operation_scope VARCHAR(200) NOT NULL,

    idempotency_key VARCHAR(255) NOT NULL,

    request_hash VARCHAR(64) NOT NULL,

    response_status INTEGER,

    response_body TEXT,

    resource_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at TIMESTAMPTZ,

    CONSTRAINT uq_idempotency_scope_key
        UNIQUE (operation_scope, idempotency_key)
);