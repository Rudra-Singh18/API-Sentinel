CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_users_email UNIQUE (email)
);

CREATE TABLE monitors (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    target_url VARCHAR(2048) NOT NULL,
    http_method VARCHAR(8) NOT NULL,
    expected_status INTEGER NOT NULL,
    interval_seconds INTEGER NOT NULL,
    timeout_ms INTEGER NOT NULL,
    failure_threshold INTEGER NOT NULL,
    recovery_threshold INTEGER NOT NULL,
    response_time_threshold_ms INTEGER NOT NULL DEFAULT 1200,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    current_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    consecutive_successes INTEGER NOT NULL DEFAULT 0,
    next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_monitors_interval CHECK (interval_seconds IN (60, 300, 900)),
    CONSTRAINT ck_monitors_method CHECK (http_method IN ('GET', 'HEAD')),
    CONSTRAINT ck_monitors_thresholds CHECK (failure_threshold BETWEEN 1 AND 10 AND recovery_threshold BETWEEN 1 AND 10)
);
CREATE INDEX ix_monitors_due ON monitors(enabled, archived, next_check_at);
CREATE INDEX ix_monitors_owner ON monitors(owner_id, archived, created_at DESC);

CREATE TABLE check_results (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    http_status INTEGER,
    response_time_ms BIGINT,
    failure_category VARCHAR(32),
    diagnostic_message VARCHAR(240),
    CONSTRAINT ck_check_outcome CHECK (outcome IN ('AVAILABLE', 'UNAVAILABLE'))
);
CREATE INDEX ix_checks_monitor_time ON check_results(monitor_id, completed_at DESC);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    state VARCHAR(16) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    failure_category VARCHAR(32),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_incident_state CHECK (state IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);
CREATE INDEX ix_incidents_monitor_state ON incidents(monitor_id, state, started_at DESC);