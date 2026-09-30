CREATE SEQUENCE demand_protocol_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE demands (
    id                      UUID PRIMARY KEY,
    protocol                VARCHAR(30) UNIQUE,
    title                   VARCHAR(200),
    source                  VARCHAR(20) NOT NULL,
    requester_id            UUID REFERENCES users (id),
    project_id              BIGINT REFERENCES projects (id),
    demand_type_id          BIGINT REFERENCES demand_types (id),
    priority_code           VARCHAR(10) REFERENCES priorities (code),
    owner_id                UUID REFERENCES users (id),
    workflow_id             BIGINT REFERENCES workflow_definitions (id),
    current_stage_id        BIGINT REFERENCES workflow_stages (id),
    lifecycle_state         VARCHAR(20) NOT NULL,
    read_only               BOOLEAN NOT NULL DEFAULT FALSE,

    requester_area          VARCHAR(200),
    requester_management    VARCHAR(200),
    requester_phone         VARCHAR(40),
    sponsor_name            VARCHAR(200),

    objective               TEXT,
    current_problem         TEXT,
    justification           TEXT,
    expected_benefits       TEXT,
    scope_description       TEXT,
    out_of_scope            TEXT,

    impacted_areas          VARCHAR(1000),
    impacted_users_count    INT,
    systems_involved        VARCHAR(1000),
    impact_level            VARCHAR(20),
    urgency                 VARCHAR(20),
    desired_date            DATE,
    deadline_justification  VARCHAR(2000),
    regulatory_requirement  BOOLEAN NOT NULL DEFAULT FALSE,
    regulatory_description  VARCHAR(2000),

    has_budget_impact       BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_budget        NUMERIC(15, 2),
    cost_center             VARCHAR(100),
    budget_approved         BOOLEAN,
    expected_return         VARCHAR(2000),

    business_focal_point    VARCHAR(200),
    technical_focal_point   VARCHAR(200),
    other_stakeholders      VARCHAR(2000),

    dependencies            TEXT,
    known_risks             TEXT,
    additional_notes        TEXT,
    additional_data         TEXT,

    original_id             VARCHAR(100),
    original_created_at     TIMESTAMP WITH TIME ZONE,
    original_status         VARCHAR(200),
    original_owner          VARCHAR(200),

    submitted_at            TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    stage_entered_at        TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_demands_requester ON demands (requester_id);
CREATE INDEX ix_demands_stage ON demands (current_stage_id);
CREATE INDEX ix_demands_project ON demands (project_id);

CREATE TABLE demand_stage_history (
    id          UUID PRIMARY KEY,
    demand_id   UUID        NOT NULL REFERENCES demands (id),
    stage_code  VARCHAR(60) NOT NULL,
    stage_name  VARCHAR(200) NOT NULL,
    category    VARCHAR(30) NOT NULL,
    action      VARCHAR(60),
    actor_id    UUID REFERENCES users (id),
    actor_name  VARCHAR(200),
    reason      VARCHAR(2000),
    entered_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    exited_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_stage_history_demand ON demand_stage_history (demand_id);

CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    demand_id         UUID         NOT NULL REFERENCES demands (id),
    kind              VARCHAR(30)  NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(150) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    extracted_text    TEXT,
    extraction_status VARCHAR(20)  NOT NULL,
    extraction_error  VARCHAR(1000),
    uploaded_by       UUID REFERENCES users (id),
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_documents_demand ON documents (demand_id);

CREATE TABLE information_requests (
    id            UUID PRIMARY KEY,
    demand_id     UUID NOT NULL REFERENCES demands (id),
    question      VARCHAR(4000) NOT NULL,
    requested_by  UUID REFERENCES users (id),
    requested_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    response      VARCHAR(8000),
    responded_by  UUID REFERENCES users (id),
    responded_at  TIMESTAMP WITH TIME ZONE,
    status        VARCHAR(20) NOT NULL
);
CREATE INDEX ix_info_requests_demand ON information_requests (demand_id);

CREATE TABLE comments (
    id          UUID PRIMARY KEY,
    demand_id   UUID NOT NULL REFERENCES demands (id),
    author_id   UUID REFERENCES users (id),
    author_name VARCHAR(200) NOT NULL,
    body        VARCHAR(8000) NOT NULL,
    visibility  VARCHAR(20) NOT NULL,
    source      VARCHAR(20) NOT NULL,
    external_id VARCHAR(100),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_comments_demand ON comments (demand_id);

CREATE TABLE approvals (
    id            UUID PRIMARY KEY,
    demand_id     UUID NOT NULL REFERENCES demands (id),
    rule_id       BIGINT REFERENCES approval_rules (id),
    rule_name     VARCHAR(200) NOT NULL,
    stage_code    VARCHAR(60) NOT NULL,
    approver_role VARCHAR(40) NOT NULL REFERENCES roles (code),
    status        VARCHAR(20) NOT NULL,
    decided_by    UUID REFERENCES users (id),
    decided_at    TIMESTAMP WITH TIME ZONE,
    comment       VARCHAR(4000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_approvals_demand ON approvals (demand_id);
CREATE INDEX ix_approvals_status ON approvals (status, approver_role);
