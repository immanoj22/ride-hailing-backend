-- admin-service initial schema
-- Mirrors the Mongoose models in Admin-service/modals/*.schema.js:
--   admin.schema.js, adminactivity.schema.js, cms.schema.js, coupon.schema.js,
--   couponUsage.schema.js, faq.schema.js, settings.schema.js

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- admins  (admin.schema.js)
-- ============================================================================
CREATE TABLE admins (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    fullname            VARCHAR(150)  NOT NULL DEFAULT '',
    email               VARCHAR(150)  NOT NULL DEFAULT '',
    password            VARCHAR(255)  NOT NULL DEFAULT '',
    mobileno            VARCHAR(20)   NOT NULL DEFAULT '',
    profile             VARCHAR(500)  NOT NULL DEFAULT '',
    isactive            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_admins_legacy_mongo_id ON admins (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_admins_email ON admins (email);

-- ============================================================================
-- admin_activities  (adminactivity.schema.js) - login/logout/CRUD audit trail
-- ============================================================================
CREATE TABLE admin_activities (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    admin_id            UUID NOT NULL REFERENCES admins (id),
    action              VARCHAR(100)  NOT NULL,
    ip                  VARCHAR(64)   NOT NULL DEFAULT '',
    device              VARCHAR(255)  NOT NULL DEFAULT '',
    meta                JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_admin_activities_legacy_mongo_id ON admin_activities (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_admin_activities_admin_id ON admin_activities (admin_id);

-- ============================================================================
-- cms_pages  (cms.schema.js) - CMS/static pages + page-embedded FAQ entries
-- ============================================================================
CREATE TABLE cms_pages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    name                VARCHAR(150)  NOT NULL DEFAULT '',
    slug                VARCHAR(150)  NOT NULL DEFAULT '',
    page                VARCHAR(50)   NOT NULL DEFAULT '',
    type                INTEGER,
    link                VARCHAR(500)  NOT NULL DEFAULT '',
    image               VARCHAR(500)  NOT NULL DEFAULT '',
    question            TEXT          NOT NULL DEFAULT '',
    answer              TEXT          NOT NULL DEFAULT '',
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_cms_pages_legacy_mongo_id ON cms_pages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_cms_pages_name ON cms_pages (name);
CREATE INDEX idx_cms_pages_page ON cms_pages (page);
CREATE INDEX idx_cms_pages_is_deleted ON cms_pages (is_deleted);

-- ============================================================================
-- faqs  (faq.schema.js)
-- ============================================================================
CREATE TABLE faqs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    question            TEXT          NOT NULL DEFAULT '',
    answer              TEXT          NOT NULL DEFAULT '',
    type                VARCHAR(10)   NOT NULL DEFAULT 'user' CHECK (type IN ('user', 'driver')),
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_faqs_legacy_mongo_id ON faqs (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_faqs_type ON faqs (type);
CREATE INDEX idx_faqs_is_deleted ON faqs (is_deleted);

-- ============================================================================
-- coupons  (coupon.schema.js)
-- ============================================================================
CREATE TABLE coupons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    title               VARCHAR(150)  NOT NULL DEFAULT '',
    description         TEXT          NOT NULL DEFAULT '',
    code                VARCHAR(100)  NOT NULL DEFAULT '',
    image               VARCHAR(500)  NOT NULL DEFAULT '',
    trip_type           VARCHAR(20)   NOT NULL DEFAULT 'local' CHECK (trip_type IN ('local', 'outstation')),
    discount_type       VARCHAR(20)   NOT NULL DEFAULT 'amount' CHECK (discount_type IN ('amount', 'percentage')),
    discount_value      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    start_date          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expiry_date         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- eligibleUsers: [ObjectId] in Mongoose -> ids owned by user-service, no FK possible/allowed
    eligible_users      UUID[]        NOT NULL DEFAULT '{}',
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_coupons_legacy_mongo_id ON coupons (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_coupons_code ON coupons (code);
CREATE INDEX idx_coupons_trip_type ON coupons (trip_type);
CREATE INDEX idx_coupons_discount_type ON coupons (discount_type);
CREATE INDEX idx_coupons_is_deleted ON coupons (is_deleted);
CREATE INDEX idx_coupons_created_at ON coupons (created_at);

-- ============================================================================
-- coupon_usages  (couponUsage.schema.js)
-- ============================================================================
CREATE TABLE coupon_usages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    coupon_id           UUID REFERENCES coupons (id) ON DELETE SET NULL,
    -- userId/rideId: owned by user-service/ride-service respectively, no FK
    user_id             UUID,
    ride_id             UUID,
    used_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    discount_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_coupon_usages_legacy_mongo_id ON coupon_usages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_coupon_usages_coupon_id_user_id ON coupon_usages (coupon_id, user_id);
CREATE INDEX idx_coupon_usages_user_id ON coupon_usages (user_id);

-- ============================================================================
-- settings  (settings.schema.js) - effectively a singleton row
-- ============================================================================
CREATE TABLE settings (
    id                                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id                         VARCHAR(24),
    ios_version                             VARCHAR(20)   NOT NULL DEFAULT '',
    android_version                         VARCHAR(20)   NOT NULL DEFAULT '',
    ios_link                                VARCHAR(500)  NOT NULL DEFAULT '',
    android_link                            VARCHAR(500)  NOT NULL DEFAULT '',
    driver_referral_percent                 NUMERIC(5, 2) NOT NULL DEFAULT 1,
    user_referral_percent                   NUMERIC(5, 2) NOT NULL DEFAULT 1,
    referral_enabled                        BOOLEAN       NOT NULL DEFAULT TRUE,
    customer_referral_reward_type           VARCHAR(20)   NOT NULL DEFAULT 'wallet_credit'
                                                 CHECK (customer_referral_reward_type IN ('wallet_credit', 'trip_discount')),
    customer_referral_wallet_credit         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    customer_referral_discount_percent      NUMERIC(5, 2) NOT NULL DEFAULT 0,
    customer_referral_discount_trip_count   INTEGER       NOT NULL DEFAULT 1,
    driver_referral_revenue_percent         NUMERIC(5, 2) NOT NULL DEFAULT 0,
    admin_percent                           NUMERIC(5, 2) NOT NULL DEFAULT 1,
    user_max_count                          INTEGER       NOT NULL DEFAULT 1,
    local_ride_km_limit                     NUMERIC(8, 2) NOT NULL DEFAULT 1,
    created_at                              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                              TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_settings_legacy_mongo_id ON settings (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
