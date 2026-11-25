-- user-service initial schema
-- Source (Mongoose): modals/user.schema.js, modals/address.schema.js, modals/useractivity.schema.js,
--                     modals/userReferralEarning.schema.js
--
-- UUID primary keys via pgcrypto's gen_random_uuid().
-- Every table gets a nullable/unique/indexed legacy_mongo_id for the future data-migration/backfill.
-- Enum-like Mongoose string fields are modelled as varchar + CHECK constraints (kept consistent
-- across this service rather than native Postgres ENUM types, to keep Hibernate mapping simple).
-- Cross-service references (ride ids, coupon ids, etc.) that this service does not own stay as
-- plain uuid/text columns or jsonb arrays - no foreign keys to other services' data.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================================
-- users  (modals/user.schema.js)
-- ============================================================================================
CREATE TABLE users (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id                     VARCHAR(24),

    firstname                           VARCHAR(100) NOT NULL DEFAULT '',
    lastname                            VARCHAR(100) NOT NULL DEFAULT '',
    fullname                            VARCHAR(200) NOT NULL DEFAULT '',
    fcm                                 TEXT NOT NULL DEFAULT '',
    socketstatus                        BOOLEAN NOT NULL DEFAULT FALSE,
    socket_id                           VARCHAR(255) NOT NULL DEFAULT '',
    state                               VARCHAR(100) NOT NULL DEFAULT '',
    city                                VARCHAR(100) NOT NULL DEFAULT '',
    terms_and_service_accept            BOOLEAN NOT NULL DEFAULT FALSE,
    gender                              VARCHAR(20) NOT NULL DEFAULT '',
    -- Mongoose: unique + sparse -> nullable + unique (Postgres unique constraints already treat
    -- NULLs as distinct, which is the sparse-index behaviour Mongo needed the "sparse" flag for).
    email                               VARCHAR(255),
    profile                             TEXT NOT NULL DEFAULT '',
    -- Mongoose declared mobile "unique" (non-sparse) but Google sign-up never sets it, which would
    -- collide on the schema's own default(""); nullable+unique reproduces the intended "one mobile
    -- per account" rule without that latent multi-empty-string bug.
    mobile                              VARCHAR(20),
    is_deleted                          BOOLEAN NOT NULL DEFAULT FALSE,
    userid                              VARCHAR(50) NOT NULL DEFAULT '',
    otp                                 VARCHAR(10) NOT NULL DEFAULT '',
    otp_expire                          BIGINT NOT NULL DEFAULT 0,
    otp_verify                          BOOLEAN NOT NULL DEFAULT FALSE,

    -- currentLocation (GeoJSON Point), stored as plain lat/lng - no geo queries in the original code.
    current_location_lat                DOUBLE PRECISION,
    current_location_lng                DOUBLE PRECISION,

    -- tripid / emergencyContacts: Mongoose Mixed arrays with no fixed shape.
    trip_ids                            JSONB NOT NULL DEFAULT '[]'::jsonb,
    emergency_contacts                  JSONB NOT NULL DEFAULT '[]'::jsonb,

    profile_skip                        BOOLEAN NOT NULL DEFAULT FALSE,

    -- preferences.*
    allow_sms_invoice                   BOOLEAN NOT NULL DEFAULT TRUE,
    allow_whatsapp_updates              BOOLEAN NOT NULL DEFAULT TRUE,
    allow_email_invoice                 BOOLEAN NOT NULL DEFAULT TRUE,
    allow_mobile_notification           BOOLEAN NOT NULL DEFAULT TRUE,

    -- wallet.*
    wallet_current_amount               NUMERIC(12,2) NOT NULL DEFAULT 0,
    wallet_last_updated                 TIMESTAMPTZ NOT NULL DEFAULT now(),

    referral_code                       VARCHAR(20),
    referred_by                         UUID REFERENCES users(id),
    referrals                           JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- referralReward.*
    referral_reward_type                VARCHAR(20)
        CHECK (referral_reward_type IN ('wallet_credit', 'trip_discount')),
    referral_reward_amount              NUMERIC(12,2) NOT NULL DEFAULT 0,
    referral_reward_discount_percent    NUMERIC(5,2) NOT NULL DEFAULT 0,
    referral_reward_remaining_trips     INTEGER NOT NULL DEFAULT 0,
    referral_reward_used_ride_ids       JSONB NOT NULL DEFAULT '[]'::jsonb,
    referral_reward_granted_at          TIMESTAMPTZ,

    wallet_balance                      NUMERIC(12,2) NOT NULL DEFAULT 0,

    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_legacy_mongo_id ON users (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uk_users_mobile ON users (mobile) WHERE mobile IS NOT NULL;
CREATE UNIQUE INDEX uk_users_referral_code ON users (referral_code) WHERE referral_code IS NOT NULL;
CREATE INDEX idx_users_referred_by ON users (referred_by);
CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_users_is_deleted ON users (is_deleted);
CREATE INDEX idx_users_firstname ON users (firstname);
CREATE INDEX idx_users_lastname ON users (lastname);
CREATE INDEX idx_users_fullname ON users (fullname);
CREATE INDEX idx_users_gender ON users (gender);

-- ============================================================================================
-- addresses  (modals/address.schema.js)
-- ============================================================================================
CREATE TABLE addresses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id   VARCHAR(24),

    user_id           UUID NOT NULL REFERENCES users(id),
    label             VARCHAR(100) NOT NULL DEFAULT '',
    street1           VARCHAR(255) NOT NULL DEFAULT '',
    street2           VARCHAR(255) NOT NULL DEFAULT '',
    district          VARCHAR(100) NOT NULL DEFAULT '',
    landmark          VARCHAR(255) NOT NULL DEFAULT '',
    type              VARCHAR(20) NOT NULL DEFAULT 'home' CHECK (type IN ('home', 'work', 'other')),
    -- Mongoose stored this as a String("0"/"1") default; a real boolean carries the same meaning.
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,

    -- coordinate (GeoJSON Point)
    coordinate_lat    DOUBLE PRECISION,
    coordinate_lng    DOUBLE PRECISION,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_addresses_legacy_mongo_id ON addresses (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_addresses_user_id ON addresses (user_id);

-- ============================================================================================
-- user_activities  (modals/useractivity.schema.js, Mongoose model "Activity")
-- ============================================================================================
CREATE TABLE user_activities (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id   VARCHAR(24),

    user_id           UUID NOT NULL REFERENCES users(id),
    action            VARCHAR(100) NOT NULL,
    ip                VARCHAR(64) NOT NULL DEFAULT '',
    device            VARCHAR(255) NOT NULL DEFAULT '',
    meta              JSONB NOT NULL DEFAULT '{}'::jsonb,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_user_activities_legacy_mongo_id ON user_activities (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_user_activities_user_id ON user_activities (user_id);
CREATE INDEX idx_user_activities_created_at ON user_activities (created_at);

-- ============================================================================================
-- user_referral_earnings  (modals/userReferralEarning.schema.js)
-- ============================================================================================
CREATE TABLE user_referral_earnings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id    VARCHAR(24),

    referrer_id        UUID NOT NULL REFERENCES users(id),
    referred_user_id   UUID NOT NULL UNIQUE REFERENCES users(id),
    amount             NUMERIC(12,2) NOT NULL DEFAULT 0,
    reward_type        VARCHAR(20) NOT NULL CHECK (reward_type IN ('wallet_credit', 'trip_discount')),
    discount_percent   NUMERIC(5,2) NOT NULL DEFAULT 0,
    trip_count         INTEGER NOT NULL DEFAULT 0,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_user_referral_earnings_legacy_mongo_id ON user_referral_earnings (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_user_referral_earnings_referrer_id ON user_referral_earnings (referrer_id);
CREATE INDEX idx_user_referral_earnings_created_at ON user_referral_earnings (created_at);
