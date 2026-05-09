-- Schema for scan-report storage. Mirrors the JPA entities in
-- core/src/main/kotlin/no/novari/linkwalker/report/jpa so a Go scanner
-- can write reports the Kotlin reader serves and vice versa.
--
-- All statements are idempotent: safe to run on every startup, no harm
-- if Hibernate's ddl-auto already created the tables for the JVM apps.

CREATE TABLE IF NOT EXISTS report_summary (
    id                 UUID         PRIMARY KEY,
    scan_id            UUID         NOT NULL,
    org_id             VARCHAR(64)  NOT NULL,
    scan_completed_at  TIMESTAMPTZ  NOT NULL,
    summary_json       TEXT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_summary_org_completed
    ON report_summary (org_id, scan_completed_at DESC);

CREATE TABLE IF NOT EXISTS report_row (
    id                     BIGSERIAL    PRIMARY KEY,
    scan_id                UUID         NOT NULL,
    org_id                 VARCHAR(64)  NOT NULL,
    scan_completed_at      TIMESTAMPTZ  NOT NULL,
    component              VARCHAR(64)  NOT NULL,
    resource               VARCHAR(64)  NOT NULL,
    problem_type           VARCHAR(64)  NOT NULL,
    source_self            TEXT         NOT NULL,
    target_href            TEXT         NOT NULL,
    relation_name          VARCHAR(128),
    expected_inverse_name  VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_row_org_completed
    ON report_row (org_id, scan_completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_row_filter
    ON report_row (org_id, scan_id, component, resource, problem_type);
