-- driver-service initial schema
-- Mongo collections migrated: driver (driver.schema.js), Activity (driveractivity.schema.js),
-- driverWallet (driverwallet.schema.js), driverWalletTransaction (driverWalletTransaction.schema.js)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================
-- drivers  (driver.schema.js)
-- =====================================================================
CREATE TABLE drivers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id      VARCHAR(24),

    mobile               VARCHAR(20),
    email                VARCHAR(255),

    first_name           VARCHAR(100) NOT NULL DEFAULT '',
    last_name            VARCHAR(100) NOT NULL DEFAULT '',
    gender               VARCHAR(20)  NOT NULL DEFAULT '',
    state                VARCHAR(100) NOT NULL DEFAULT '',
    city                 VARCHAR(100) NOT NULL DEFAULT '',
    profile_completed    BOOLEAN NOT NULL DEFAULT FALSE,

    fcm_token            VARCHAR(1000) NOT NULL DEFAULT '',
    socket_id            VARCHAR(255) NOT NULL DEFAULT '',
    socket_online        BOOLEAN NOT NULL DEFAULT FALSE,

    status               VARCHAR(20) NOT NULL DEFAULT 'pending',

    kyc_completed        BOOLEAN NOT NULL DEFAULT FALSE,
    kyc_status           VARCHAR(20) NOT NULL DEFAULT 'pending',
    profile_image        VARCHAR(500) NOT NULL DEFAULT '',
    unverified_driver    BOOLEAN NOT NULL DEFAULT TRUE,

    -- vehicle-service owns vehicle types/variants - plain uuid columns, no FK
    vehicle_number       VARCHAR(50) NOT NULL DEFAULT '',
    chassis_number       VARCHAR(100) NOT NULL DEFAULT '',
    engine_number        VARCHAR(100) NOT NULL DEFAULT '',
    fuel_type            VARCHAR(50) NOT NULL DEFAULT '',
    vehicle_type         UUID,
    vehicle_variant      UUID,

    driver_status        VARCHAR(20) NOT NULL DEFAULT 'offline',
    last_online_at       TIMESTAMPTZ,

    -- driver.schema.js currentLocation GeoJSON Point (2dsphere index). No PostGIS extension is
    -- provisioned for this service; plain lat/lng columns + btree index preserve the equality/
    -- filter behaviour, but a true $near radius search (nearby_drivers) will need either a
    -- PostGIS geography column or an application-side bounding-box + Haversine filter later.
    current_latitude     DOUBLE PRECISION,
    current_longitude    DOUBLE PRECISION,

    -- driver.schema.js preferredLocations is an untyped Array (default []); some call sites treat
    -- it as GeoJSON points, others (nearby_outstation_drivers) treat it as plain district-name
    -- strings - genuinely variable-shape, so kept as jsonb rather than a fixed column/child table.
    preferred_locations  JSONB NOT NULL DEFAULT '[]'::jsonb,

    on_ride              BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    delete_reason        VARCHAR(500) NOT NULL DEFAULT '',

    -- ride-service owns trips - plain uuid[] of the last few trip ids (driver.schema.js "trips"
    -- array, capped at 5 via $push/$slice in updateDriverTrip), no FK.
    recent_trip_ids      UUID[] NOT NULL DEFAULT '{}',
    last_trip_at         TIMESTAMPTZ,

    -- driver.schema.js bankDetails embedded object (no array/own _id) - flattened to columns.
    bank_account_number  VARCHAR(50)  NOT NULL DEFAULT '',
    bank_ifsc            VARCHAR(20)  NOT NULL DEFAULT '',
    bank_holder_name     VARCHAR(150) NOT NULL DEFAULT '',
    bank_name            VARCHAR(150) NOT NULL DEFAULT '',
    bank_branch          VARCHAR(150) NOT NULL DEFAULT '',
    bank_upi_id          VARCHAR(150) NOT NULL DEFAULT '',
    bank_upi_qr_image    VARCHAR(500) NOT NULL DEFAULT '',

    otp                  VARCHAR(10) NOT NULL DEFAULT '',
    otp_expires_at       TIMESTAMPTZ,

    -- referralCode is not declared unique in the Mongoose schema, but getRefCodeWithCheck()
    -- retries generation until unique - indexed (not unique-constrained) to match declared intent
    -- without changing behaviour if that retry loop ever raced.
    referral_code        VARCHAR(20),
    -- self-referencing FK (driver.schema.js referredBy ref: "driver") - internal relationship.
    referred_by          UUID REFERENCES drivers (id),
    -- driver.schema.js "referrals" array is the inverse of referredBy and is intentionally NOT
    -- duplicated here - it is derivable via "WHERE referred_by = :driverId" (see
    -- DriverRepository.findByReferredBy), avoiding a redundant denormalized list.
    -- driver.schema.js "wallet" ref is likewise intentionally NOT duplicated here - driver_wallets
    -- already carries the owning driver_id FK (see DriverWalletRepository.findByDriverId).

    is_blocked           BOOLEAN NOT NULL DEFAULT FALSE,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_drivers_status CHECK (status IN ('pending', 'verified', 'rejected')),
    CONSTRAINT chk_drivers_kyc_status CHECK (kyc_status IN ('pending', 'approved', 'rejected', 'suspended')),
    CONSTRAINT chk_drivers_driver_status CHECK (driver_status IN (
        'offline', 'online', 'requestride', 'rideaccept', 'pickup', 'startride', 'endtrip', 'searchrides'
    ))
);

CREATE UNIQUE INDEX uq_drivers_legacy_mongo_id ON drivers (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE UNIQUE INDEX uq_drivers_mobile ON drivers (mobile) WHERE mobile IS NOT NULL;
CREATE UNIQUE INDEX uq_drivers_email ON drivers (email) WHERE email IS NOT NULL;
CREATE INDEX idx_drivers_referral_code ON drivers (referral_code);
CREATE INDEX idx_drivers_referred_by ON drivers (referred_by);
CREATE INDEX idx_drivers_vehicle_type ON drivers (vehicle_type);
CREATE INDEX idx_drivers_vehicle_variant ON drivers (vehicle_variant);
CREATE INDEX idx_drivers_driver_status ON drivers (driver_status);
CREATE INDEX idx_drivers_kyc_status ON drivers (kyc_status);
CREATE INDEX idx_drivers_kyc_completed ON drivers (kyc_completed);
CREATE INDEX idx_drivers_unverified_driver ON drivers (unverified_driver);
CREATE INDEX idx_drivers_is_deleted ON drivers (is_deleted);
CREATE INDEX idx_drivers_on_ride ON drivers (on_ride);
CREATE INDEX idx_drivers_created_at ON drivers (created_at DESC);
CREATE INDEX idx_drivers_current_location ON drivers (current_latitude, current_longitude);

-- =====================================================================
-- driver_documents  (driver.schema.js embedded "documents" subdocuments)
-- =====================================================================
CREATE TABLE driver_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id    UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    doc_key      VARCHAR(50) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'pending',
    -- files/fileStatus are free-form key->value maps (sub-key or index -> url/status) in the
    -- original schema (type: Object) - genuinely variable-shape, kept as jsonb.
    files        JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_status  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_driver_documents_status CHECK (status IN ('pending', 'verified', 'rejected')),
    CONSTRAINT uq_driver_documents_driver_key UNIQUE (driver_id, doc_key)
);

CREATE INDEX idx_driver_documents_driver_id ON driver_documents (driver_id);

-- =====================================================================
-- driver_emergency_contacts  (driver.schema.js embedded "emergencyContacts" subdocuments)
-- =====================================================================
CREATE TABLE driver_emergency_contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    relation    VARCHAR(50) NOT NULL DEFAULT '',
    phone       VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_driver_emergency_contacts_driver_id ON driver_emergency_contacts (driver_id);

-- =====================================================================
-- driver_activities  (driveractivity.schema.js "Activity" model)
-- =====================================================================
CREATE TABLE driver_activities (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    driver_id    UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    action       VARCHAR(100) NOT NULL,
    ip           VARCHAR(64) NOT NULL DEFAULT '',
    device       VARCHAR(500) NOT NULL DEFAULT '',
    meta         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_driver_activities_legacy_mongo_id ON driver_activities (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_driver_activities_driver_id ON driver_activities (driver_id);
CREATE INDEX idx_driver_activities_action ON driver_activities (action);
CREATE INDEX idx_driver_activities_created_at ON driver_activities (created_at DESC);

-- =====================================================================
-- driver_wallets  (driverwallet.schema.js)
-- =====================================================================
CREATE TABLE driver_wallets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id   VARCHAR(24),
    driver_id         UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    current_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    claim_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    last_claim_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
    wallet_status     VARCHAR(20) NOT NULL DEFAULT 'active',
    pending_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_driver_wallets_status CHECK (wallet_status IN ('active', 'inactive'))
);

CREATE UNIQUE INDEX uq_driver_wallets_legacy_mongo_id ON driver_wallets (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
-- Mongoose only declares "index: true" (not unique) on driverId - preserved as a plain index.
CREATE INDEX idx_driver_wallets_driver_id ON driver_wallets (driver_id);

-- =====================================================================
-- driver_wallet_transactions  (driverWalletTransaction.schema.js)
-- =====================================================================
CREATE TABLE driver_wallet_transactions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id    VARCHAR(24),
    driver_id          UUID NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    requested_amount   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    requested_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status             VARCHAR(20) NOT NULL DEFAULT 'pending',
    transaction_type   VARCHAR(20) NOT NULL DEFAULT 'credit',
    -- metaData is a free-form object (e.g. referredDriverId for referral bonuses) - jsonb.
    meta_data          JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- reviewedBy/paidBy are admin (or in-app) actor ids - cross-service, no FK.
    reviewed_by        UUID,
    reviewed_at        TIMESTAMPTZ,
    paid_by            UUID,
    paid_at            TIMESTAMPTZ,
    paid_amount        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    note               VARCHAR(1000) NOT NULL DEFAULT '',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_driver_wallet_tx_status CHECK (status IN ('pending', 'rejected', 'approved', 'paid')),
    CONSTRAINT chk_driver_wallet_tx_type CHECK (
        transaction_type IN ('credit', 'debit', 'claim', 'refund', 'adjustment', 'referral_bonus')
    )
);

CREATE UNIQUE INDEX uq_driver_wallet_tx_legacy_mongo_id ON driver_wallet_transactions (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_driver_wallet_tx_driver_id ON driver_wallet_transactions (driver_id);
CREATE INDEX idx_driver_wallet_tx_status ON driver_wallet_transactions (status);
CREATE INDEX idx_driver_wallet_tx_type ON driver_wallet_transactions (transaction_type);
CREATE INDEX idx_driver_wallet_tx_driver_type_status ON driver_wallet_transactions (driver_id, transaction_type, status);
CREATE INDEX idx_driver_wallet_tx_created_at ON driver_wallet_transactions (created_at DESC);

-- One welcome-referral reward per referred driver, mirroring driverWalletTransaction.schema.js's
-- partial unique index on { driverId, "metaData.referredDriverId" } (only when that key exists).
CREATE UNIQUE INDEX uq_driver_wallet_tx_referral ON driver_wallet_transactions (driver_id, (meta_data ->> 'referredDriverId'))
    WHERE meta_data ? 'referredDriverId';
