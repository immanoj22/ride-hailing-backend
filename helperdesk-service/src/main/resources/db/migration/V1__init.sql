-- helperdesk-service initial schema
-- Mongo collections migrated: support (support.schema.js) and SOS (sos.schema.js)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================
-- support_tickets  (support.schema.js top-level fields)
-- =====================================================================
CREATE TABLE support_tickets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id   VARCHAR(24),
    -- owning user or driver id (owned by user-service/driver-service - no FK here)
    user_id           UUID,
    subject           VARCHAR(255) NOT NULL DEFAULT '',
    from_type         VARCHAR(10) NOT NULL,
    complain_type     VARCHAR(20),
    status            BOOLEAN NOT NULL DEFAULT TRUE,
    closing_message   TEXT NOT NULL DEFAULT '',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_tickets_from_type CHECK (from_type IN ('user', 'driver')),
    CONSTRAINT chk_support_tickets_complain_type CHECK (
        complain_type IS NULL OR complain_type IN ('refund', 'ride issue', 'payment issue', 'other')
    )
);

CREATE UNIQUE INDEX uq_support_tickets_legacy_mongo_id ON support_tickets (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_support_tickets_user_id ON support_tickets (user_id);
CREATE INDEX idx_support_tickets_from_type ON support_tickets (from_type);
CREATE INDEX idx_support_tickets_complain_type ON support_tickets (complain_type);
CREATE INDEX idx_support_tickets_status ON support_tickets (status);
CREATE INDEX idx_support_tickets_created_at ON support_tickets (created_at DESC);

-- =====================================================================
-- support_messages  (support.schema.js embedded "messages" subdocuments)
-- =====================================================================
CREATE TABLE support_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    support_ticket_id   UUID NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    message             TEXT NOT NULL DEFAULT '',
    image               TEXT NOT NULL DEFAULT '',
    sender              VARCHAR(10) NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_messages_sender CHECK (sender IN ('creator', 'admin', 'driver', 'user', ''))
);

CREATE UNIQUE INDEX uq_support_messages_legacy_mongo_id ON support_messages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_support_messages_ticket_id ON support_messages (support_ticket_id);
CREATE INDEX idx_support_messages_created_at ON support_messages (created_at);

-- =====================================================================
-- sos_alerts  (sos.schema.js)
-- =====================================================================
CREATE TABLE sos_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    -- ride-service owns rides, user/driver-service own the rider/driver - no FK here
    ride_id         UUID,
    user_id         UUID,
    user_type       VARCHAR(10) NOT NULL DEFAULT '',
    location_lat    DOUBLE PRECISION,
    location_lng    DOUBLE PRECISION,
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sos_alerts_user_type CHECK (user_type IN ('rider', 'driver', ''))
);

CREATE UNIQUE INDEX uq_sos_alerts_legacy_mongo_id ON sos_alerts (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_sos_alerts_ride_id ON sos_alerts (ride_id);
CREATE INDEX idx_sos_alerts_user_id ON sos_alerts (user_id);
CREATE INDEX idx_sos_alerts_user_type ON sos_alerts (user_type);
CREATE INDEX idx_sos_alerts_created_at ON sos_alerts (created_at DESC);
CREATE INDEX idx_sos_alerts_ride_user_type ON sos_alerts (ride_id, user_id, user_type);
