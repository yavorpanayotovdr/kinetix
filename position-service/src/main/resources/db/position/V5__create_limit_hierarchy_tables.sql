CREATE TABLE limit_definitions (
    id VARCHAR(255) PRIMARY KEY,
    level VARCHAR(20) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    limit_type VARCHAR(20) NOT NULL,
    limit_value DECIMAL(28, 12) NOT NULL,
    intraday_limit DECIMAL(28, 12),
    overnight_limit DECIMAL(28, 12),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (level, entity_id, limit_type)
);

CREATE INDEX idx_limit_definitions_entity ON limit_definitions(entity_id, level, limit_type);

CREATE TABLE limit_temporary_increases (
    id VARCHAR(255) PRIMARY KEY,
    limit_id VARCHAR(255) NOT NULL REFERENCES limit_definitions(id),
    new_value DECIMAL(28, 12) NOT NULL,
    approved_by VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_limit_temp_increases_limit_id ON limit_temporary_increases(limit_id, expires_at);
