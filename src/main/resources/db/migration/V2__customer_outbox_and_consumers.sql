CREATE TABLE customers
(
    id         UUID PRIMARY KEY,
    owner_id   UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    revision   BIGINT       NOT NULL CHECK (revision > 0),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_customers_owner_email UNIQUE (owner_id, email)
);
CREATE INDEX ix_customers_owner ON customers (owner_id, id);
CREATE INDEX ix_customers_retention ON customers (updated_at);

-- Deliberately no Customer FK: deletion must not remove its delivery record.
CREATE TABLE customer_outbox
(
    event_id        UUID PRIMARY KEY,
    customer_id     UUID        NOT NULL,
    owner_id        UUID        NOT NULL,
    revision        BIGINT      NOT NULL CHECK (revision > 0),
    event_type      VARCHAR(40) NOT NULL CHECK (event_type IN
                                                ('customer.created.v1', 'customer.updated.v1', 'customer.deleted.v1')),
    occurred_at     TIMESTAMPTZ NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    UNIQUE (customer_id, revision)
);
CREATE INDEX ix_customer_outbox_pending ON customer_outbox (next_attempt_at, occurred_at) WHERE status='PENDING';
CREATE INDEX ix_customer_outbox_unpublished ON customer_outbox (customer_id, revision) WHERE status <> 'PUBLISHED';
CREATE INDEX ix_customer_outbox_retention ON customer_outbox (published_at) WHERE status='PUBLISHED';

CREATE TABLE customer_audit
(
    event_id    UUID PRIMARY KEY,
    customer_id UUID        NOT NULL,
    owner_id    UUID        NOT NULL,
    revision    BIGINT      NOT NULL,
    event_type  VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (customer_id, revision)
);
CREATE INDEX ix_customer_audit_retention ON customer_audit (recorded_at);
CREATE TABLE customer_audit_cursor
(
    customer_id UUID PRIMARY KEY,
    revision    BIGINT      NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ
);
CREATE TABLE customer_notifications
(
    event_id    UUID PRIMARY KEY,
    customer_id UUID        NOT NULL,
    owner_id    UUID        NOT NULL,
    revision    BIGINT      NOT NULL,
    event_type  VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (customer_id, revision)
);
CREATE INDEX ix_customer_notifications_owner ON customer_notifications (owner_id, recorded_at DESC);
CREATE INDEX ix_customer_notifications_retention ON customer_notifications (recorded_at);
CREATE TABLE customer_notifications_cursor
(
    customer_id UUID PRIMARY KEY,
    revision    BIGINT      NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ
);

-- Retained briefly even after deletion, so a retry cannot resurrect a removed profile.
CREATE TABLE customer_creation_requests
(
    owner_id    UUID        NOT NULL,
    request_key UUID        NOT NULL,
    fingerprint BYTEA       NOT NULL,
    customer_id UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, request_key)
);
CREATE INDEX ix_customer_creation_retention ON customer_creation_requests (created_at);
