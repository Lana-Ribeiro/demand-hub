-- IA ------------------------------------------------------------------------
CREATE TABLE ai_agent_runs (
    id          UUID PRIMARY KEY,
    demand_id   UUID REFERENCES demands (id),
    agent       VARCHAR(60)  NOT NULL,
    provider    VARCHAR(40)  NOT NULL,
    model       VARCHAR(100),
    mode        VARCHAR(10)  NOT NULL,
    input       TEXT,
    output      TEXT,
    status      VARCHAR(20)  NOT NULL,
    error       VARCHAR(2000),
    duration_ms BIGINT,
    created_by  UUID REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_agent_runs_demand ON ai_agent_runs (demand_id);

CREATE TABLE ai_suggestions (
    id                UUID PRIMARY KEY,
    demand_id         UUID NOT NULL REFERENCES demands (id),
    agent_run_id      UUID REFERENCES ai_agent_runs (id),
    agent             VARCHAR(60) NOT NULL,
    kind              VARCHAR(30) NOT NULL,
    field             VARCHAR(60),
    suggested_value   TEXT,
    alternative_value TEXT,
    confidence        NUMERIC(4, 3),
    source_type       VARCHAR(20) NOT NULL,
    source_ref        VARCHAR(255),
    rationale         VARCHAR(4000),
    status            VARCHAR(20) NOT NULL,
    final_value       TEXT,
    decided_by        UUID REFERENCES users (id),
    decided_at        TIMESTAMP WITH TIME ZONE,
    decision_reason   VARCHAR(2000),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_suggestions_demand ON ai_suggestions (demand_id, status);

CREATE TABLE ai_analyses (
    id             UUID PRIMARY KEY,
    demand_id      UUID NOT NULL REFERENCES demands (id),
    agent_run_id   UUID REFERENCES ai_agent_runs (id),
    kind           VARCHAR(30) NOT NULL,
    mode           VARCHAR(10) NOT NULL,
    content        TEXT NOT NULL,
    edited_content TEXT,
    edited_by      UUID REFERENCES users (id),
    edited_at      TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_analyses_demand ON ai_analyses (demand_id, kind);

CREATE TABLE chat_messages (
    id           UUID PRIMARY KEY,
    demand_id    UUID NOT NULL REFERENCES demands (id),
    role         VARCHAR(20) NOT NULL,
    content      VARCHAR(8000) NOT NULL,
    agent_run_id UUID REFERENCES ai_agent_runs (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_chat_demand ON chat_messages (demand_id);

-- Refinamento ---------------------------------------------------------------
CREATE TABLE meetings (
    id           UUID PRIMARY KEY,
    demand_id    UUID NOT NULL REFERENCES demands (id),
    title        VARCHAR(200) NOT NULL,
    held_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    participants VARCHAR(2000),
    notes        TEXT,
    created_by   UUID REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE decisions (
    id          UUID PRIMARY KEY,
    demand_id   UUID NOT NULL REFERENCES demands (id),
    meeting_id  UUID REFERENCES meetings (id),
    type        VARCHAR(20) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    rationale   VARCHAR(4000),
    decided_by  VARCHAR(200),
    created_by  UUID REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refinement_items (
    id          UUID PRIMARY KEY,
    demand_id   UUID NOT NULL REFERENCES demands (id),
    type        VARCHAR(40) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    origin      VARCHAR(20) NOT NULL,
    created_by  UUID REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_refinement_items_demand ON refinement_items (demand_id);

-- Integrações ---------------------------------------------------------------
CREATE TABLE external_links (
    id           UUID PRIMARY KEY,
    demand_id    UUID NOT NULL REFERENCES demands (id),
    system       VARCHAR(20) NOT NULL,
    mode         VARCHAR(10) NOT NULL,
    external_id  VARCHAR(100),
    external_key VARCHAR(100),
    project_ref  VARCHAR(100),
    url          VARCHAR(1000),
    sync_status  VARCHAR(20) NOT NULL,
    last_error   VARCHAR(2000),
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_external_link UNIQUE (demand_id, system)
);

CREATE TABLE webhook_events (
    id          UUID PRIMARY KEY,
    system      VARCHAR(20) NOT NULL,
    event_type  VARCHAR(100),
    external_id VARCHAR(100),
    processed   BOOLEAN NOT NULL,
    error       VARCHAR(2000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Execução técnica ----------------------------------------------------------
CREATE TABLE technical_executions (
    id               UUID PRIMARY KEY,
    demand_id        UUID NOT NULL UNIQUE REFERENCES demands (id),
    system           VARCHAR(20) NOT NULL,
    strategy         VARCHAR(30) NOT NULL,
    status_code      VARCHAR(40) NOT NULL REFERENCES execution_statuses (code),
    external_link_id UUID REFERENCES external_links (id),
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at     TIMESTAMP WITH TIME ZONE,
    last_event_at    TIMESTAMP WITH TIME ZONE
);

CREATE TABLE execution_events (
    id           UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES technical_executions (id),
    from_status  VARCHAR(40),
    to_status    VARCHAR(40) NOT NULL,
    summary      VARCHAR(4000),
    source       VARCHAR(20) NOT NULL,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Notificações e auditoria --------------------------------------------------
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID REFERENCES users (id),
    channel      VARCHAR(20) NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    subject      VARCHAR(300) NOT NULL,
    body         VARCHAR(8000) NOT NULL,
    demand_id    UUID REFERENCES demands (id),
    status       VARCHAR(20) NOT NULL,
    error        VARCHAR(2000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, channel);

CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY,
    actor_id         UUID,
    actor_name       VARCHAR(200) NOT NULL,
    actor_roles      VARCHAR(300),
    action           VARCHAR(60)  NOT NULL,
    entity_type      VARCHAR(60)  NOT NULL,
    entity_id        VARCHAR(100),
    demand_id        UUID,
    field            VARCHAR(60),
    before_value     VARCHAR(4000),
    after_value      VARCHAR(4000),
    reason           VARCHAR(2000),
    ai_suggestion_id UUID,
    metadata         VARCHAR(4000),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_demand ON audit_logs (demand_id);
CREATE INDEX ix_audit_created ON audit_logs (created_at);
