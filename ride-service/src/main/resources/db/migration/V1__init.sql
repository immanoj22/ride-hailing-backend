-- ride-service initial schema
-- Mongo collections migrated: VehicleType, VehicleVariant, Price, Ride (+ embedded process /
-- adminPayment / driverPayment subdocuments), starratings, Activity, ridegps, RidePayment,
-- driverfetch (modals/*.schema.js).
--
-- Table creation order works around the VehicleVariant <-> Price circular reference: vehicle_types
-- -> vehicle_variants (no FK to prices yet) -> prices (FK to vehicle_types/vehicle_variants) ->
-- ALTER vehicle_variants to add the FKs to prices.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================
-- vehicle_types  (modals/vehicletype.schema.js)
-- =====================================================================
CREATE TABLE vehicle_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    type            VARCHAR(50)  NOT NULL DEFAULT '',
    label           VARCHAR(255) NOT NULL DEFAULT '',
    description     TEXT         NOT NULL DEFAULT '',
    image           TEXT         NOT NULL DEFAULT '',
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_hided        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_vehicle_types_legacy_mongo_id ON vehicle_types (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_vehicle_types_type ON vehicle_types (type);
CREATE INDEX idx_vehicle_types_is_deleted ON vehicle_types (is_deleted);

-- =====================================================================
-- vehicle_variants  (modals/vehiclevarient.schema.js)
-- local_price_id / outstation_price_id -> prices FKs are added later (ALTER TABLE below) because
-- the prices table does not exist yet.
-- =====================================================================
CREATE TABLE vehicle_variants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id     VARCHAR(24),
    vehicle_type_id     UUID REFERENCES vehicle_types (id),
    type                VARCHAR(50)  NOT NULL DEFAULT '',
    label               VARCHAR(255) NOT NULL DEFAULT '',
    description         TEXT         NOT NULL DEFAULT '',
    image               TEXT         NOT NULL DEFAULT '',
    is_local            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_outstation       BOOLEAN      NOT NULL DEFAULT FALSE,
    local_price_id      UUID,
    outstation_price_id UUID,
    seats               INTEGER      NOT NULL DEFAULT 2,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_vehicle_variants_legacy_mongo_id ON vehicle_variants (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_vehicle_variants_vehicle_type_id ON vehicle_variants (vehicle_type_id);
CREATE INDEX idx_vehicle_variants_is_deleted ON vehicle_variants (is_deleted);

-- =====================================================================
-- prices  (modals/price.schema.js)
-- =====================================================================
CREATE TABLE prices (
    id                                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id                        VARCHAR(24),
    vehicle_type_id                        UUID REFERENCES vehicle_types (id),
    vehicle_variant_id                     UUID REFERENCES vehicle_variants (id),
    brand                                  VARCHAR(255)   NOT NULL DEFAULT '',

    price_per_km                           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_per_min                          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_surge                            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    base_price                             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_per_day                          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_per_extra_km                     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_per_extra_min                    NUMERIC(12, 2) NOT NULL DEFAULT 0,

    insurance                              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    toll_tax                               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    mcd_tax                                NUMERIC(12, 2) NOT NULL DEFAULT 0,
    gst_percent                            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    commission_percent                     NUMERIC(12, 2) NOT NULL DEFAULT 0,

    peak_time_charge                       NUMERIC(12, 2) NOT NULL DEFAULT 0,

    user_cancellation_time_limit_minutes   INTEGER        NOT NULL DEFAULT 0,
    user_cancellation_charge               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    waiting_time_limit_minutes             INTEGER        NOT NULL DEFAULT 0,
    waiting_charge_per_min                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    transit_waiting_fee_per_min            NUMERIC(12, 2) NOT NULL DEFAULT 0,

    others1                                NUMERIC(12, 2) NOT NULL DEFAULT 0,
    others2                                NUMERIC(12, 2) NOT NULL DEFAULT 0,
    others3                                NUMERIC(12, 2) NOT NULL DEFAULT 0,

    payment_mode_setting                   TEXT[]         NOT NULL DEFAULT '{}',
    is_deleted                             BOOLEAN        NOT NULL DEFAULT FALSE,

    admin_commission_price                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    driver_commission_price                NUMERIC(12, 2) NOT NULL DEFAULT 0,

    -- embedded OutstationSchema (price.schema.js), flattened (_id:false subdocument)
    outstation_extra_km_fare               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_extra_minute_fare           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_waiting_charge              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_cancel_charge               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_one_way_round_driver_allowance NUMERIC(12, 2) NOT NULL DEFAULT 0,

    created_at                              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_prices_payment_mode_setting CHECK (payment_mode_setting <@ ARRAY['cash', 'upi', 'customer_wish']::text[])
);

CREATE UNIQUE INDEX uq_prices_legacy_mongo_id ON prices (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
-- mirrors priceSchema.index({ vehicleTypeId: 1, vehicleVariantId: 1, outstation: 1, delete: 1 })
-- (the "outstation" component indexed a whole subdocument in Mongo, meaningless in SQL - dropped)
CREATE INDEX idx_prices_vehicle_type_variant_deleted ON prices (vehicle_type_id, vehicle_variant_id, is_deleted);
CREATE INDEX idx_prices_vehicle_variant_id ON prices (vehicle_variant_id);

-- Now that prices exists, wire up vehicle_variants.localPrice / outstationPrice.
ALTER TABLE vehicle_variants
    ADD CONSTRAINT fk_vehicle_variants_local_price FOREIGN KEY (local_price_id) REFERENCES prices (id),
    ADD CONSTRAINT fk_vehicle_variants_outstation_price FOREIGN KEY (outstation_price_id) REFERENCES prices (id);

CREATE INDEX idx_vehicle_variants_local_price_id ON vehicle_variants (local_price_id);
CREATE INDEX idx_vehicle_variants_outstation_price_id ON vehicle_variants (outstation_price_id);

-- =====================================================================
-- price_oneway_packages  (price.schema.js "outstation.oneWayRound.packages[]" - OnewayPackageItemSchema)
-- =====================================================================
CREATE TABLE price_oneway_packages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    price_id   UUID           NOT NULL REFERENCES prices (id) ON DELETE CASCADE,
    from_km    NUMERIC(12, 2) NOT NULL,
    to_km      NUMERIC(12, 2) NOT NULL,
    base_fare  NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_price_oneway_packages_legacy_mongo_id ON price_oneway_packages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_price_oneway_packages_price_id ON price_oneway_packages (price_id);

-- =====================================================================
-- price_rental_packages  (price.schema.js "outstation.rental.packages[]" - RentalPackageItemSchema)
-- =====================================================================
CREATE TABLE price_rental_packages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    price_id        UUID           NOT NULL REFERENCES prices (id) ON DELETE CASCADE,
    duration_type   VARCHAR(10)    NOT NULL DEFAULT 'hour',
    duration_value  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    km_limit        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    base_fare       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_price_rental_packages_duration_type CHECK (duration_type IN ('hour', 'day', 'week'))
);

CREATE UNIQUE INDEX uq_price_rental_packages_legacy_mongo_id ON price_rental_packages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_price_rental_packages_price_id ON price_rental_packages (price_id);

-- =====================================================================
-- driver_fetch_configs  (modals/driverFetch.schema.js) - small singleton-style config table
-- =====================================================================
CREATE TABLE driver_fetch_configs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id    VARCHAR(24),
    within_km          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    driver_count       INTEGER        NOT NULL DEFAULT 0,
    request_per_driver INTEGER        NOT NULL DEFAULT 0,
    km_restriction     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_driver_fetch_configs_legacy_mongo_id ON driver_fetch_configs (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;

-- =====================================================================
-- rides  (modals/ride.schema.js)
-- =====================================================================
CREATE TABLE rides (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id                 VARCHAR(24),

    promo                           JSONB          NOT NULL DEFAULT '{}'::jsonb,
    booking_number                  VARCHAR(64)    UNIQUE,

    -- top-level pickup/drop coordinates
    pickup_lat                      DOUBLE PRECISION,
    pickup_lng                      DOUBLE PRECISION,
    drop_lat                        DOUBLE PRECISION,
    drop_lng                        DOUBLE PRECISION,

    location_pickup                 TEXT           NOT NULL DEFAULT '',
    location_dropoff                TEXT           NOT NULL DEFAULT '',
    location_pickup_district        VARCHAR(255)   NOT NULL DEFAULT '',
    location_dropoff_district       VARCHAR(255)   NOT NULL DEFAULT '',

    -- user-service / driver-service own these ids - plain uuid, no FK
    user_id                         UUID           NOT NULL,
    driver_id                       UUID,

    vehicle_type_id                 UUID REFERENCES vehicle_types (id),
    vehicle_variant_id              UUID REFERENCES vehicle_variants (id),

    postal_code_id                  INTEGER,

    ride_type                       VARCHAR(20)    NOT NULL DEFAULT 'local',
    payment_method                  VARCHAR(10)    NOT NULL DEFAULT '',
    status                          VARCHAR(20)    NOT NULL DEFAULT 'create',
    canceled_by                     VARCHAR(20)    NOT NULL DEFAULT '',
    reason                          TEXT           NOT NULL DEFAULT '',

    ride_time_seconds               INTEGER        NOT NULL DEFAULT 0,
    km                              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    mcd                             NUMERIC(12, 2) NOT NULL DEFAULT 0,

    price_id                        UUID REFERENCES prices (id),
    total_price                     JSONB          NOT NULL DEFAULT '{}'::jsonb,
    payment_data                    JSONB          NOT NULL DEFAULT '{}'::jsonb,
    transaction_history             JSONB          NOT NULL DEFAULT '{}'::jsonb,

    cancel_minutes                  INTEGER        NOT NULL DEFAULT 0,
    cancel_amount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cancel_amount_paid              BOOLEAN        NOT NULL DEFAULT FALSE,

    waiting_amount                  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    waiting_amount_paid             BOOLEAN        NOT NULL DEFAULT FALSE,
    waiting_minute                  INTEGER        NOT NULL DEFAULT 0,

    transit_waiting_amount          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    transit_waiting_amount_paid     BOOLEAN        NOT NULL DEFAULT FALSE,
    transit_waiting_minute          INTEGER        NOT NULL DEFAULT 0,

    is_toll                         BOOLEAN        NOT NULL DEFAULT FALSE,
    cod_payment                     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    merchant_transaction_id         VARCHAR(255)   NOT NULL DEFAULT '',

    -- driver-service owns these ids - plain uuid[] array, no FK/join table
    ignored_drivers                 UUID[]         NOT NULL DEFAULT '{}',
    requested_drivers                UUID[]        NOT NULL DEFAULT '{}',

    -- duplicate of pickup/drop above in the original Mongoose schema ("coordinates.pickup/dropoff") - preserved as-is
    coordinates_pickup_lat          DOUBLE PRECISION,
    coordinates_pickup_lng          DOUBLE PRECISION,
    coordinates_dropoff_lat         DOUBLE PRECISION,
    coordinates_dropoff_lng         DOUBLE PRECISION,

    -- embedded OutstationInfoSchema (_id:false subdocument), flattened
    outstation_trip_type            VARCHAR(10)    NOT NULL DEFAULT '',
    outstation_admin_commission     VARCHAR(20)    NOT NULL DEFAULT 'notPaid',
    outstation_extra_km             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_extra_time           INTEGER        NOT NULL DEFAULT 0,
    outstation_km_estimated         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_total_km             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    outstation_duration_estimated   INTEGER        NOT NULL DEFAULT 0,
    outstation_total_duration       INTEGER        NOT NULL DEFAULT 0,

    is_deleted                      BOOLEAN        NOT NULL DEFAULT FALSE,
    cancel_amount_on_ride           NUMERIC(12, 2) NOT NULL DEFAULT 0,

    created_by                      VARCHAR(20)    NOT NULL DEFAULT 'user',
    booking_date                    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    end_date                        TIMESTAMPTZ,

    advance_payment                 VARCHAR(20)    NOT NULL DEFAULT '',
    paid_by                         VARCHAR(20)    NOT NULL DEFAULT '',

    otp                             VARCHAR(10)    NOT NULL DEFAULT '',
    is_otp_verify                   BOOLEAN        NOT NULL DEFAULT FALSE,
    multiple_drop                   JSONB          NOT NULL DEFAULT '[]'::jsonb,

    notes                           TEXT           NOT NULL DEFAULT '',
    extra_carrier                   BOOLEAN        NOT NULL DEFAULT FALSE,
    upcoming_trip                   BOOLEAN        NOT NULL DEFAULT FALSE,

    created_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT chk_rides_ride_type CHECK (ride_type IN ('local', 'outstation')),
    CONSTRAINT chk_rides_payment_method CHECK (payment_method IN ('', 'cash', 'online', 'wallet')),
    CONSTRAINT chk_rides_status CHECK (status IN (
        '', 'create', 'accept', 'cancel', 'ignored', 'driver_cancel', 'user_cancel', 'start',
        'dropoff', 'timeout', 'endtrip', 'completepayment', 'assigned', 'updated'
    )),
    CONSTRAINT chk_rides_outstation_trip_type CHECK (outstation_trip_type IN ('', 'oneway', 'round', 'rental'))
);

CREATE UNIQUE INDEX uq_rides_legacy_mongo_id ON rides (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_rides_user_id ON rides (user_id);
CREATE INDEX idx_rides_driver_id ON rides (driver_id);
CREATE INDEX idx_rides_vehicle_type_id ON rides (vehicle_type_id);
CREATE INDEX idx_rides_vehicle_variant_id ON rides (vehicle_variant_id);
CREATE INDEX idx_rides_price_id ON rides (price_id);
CREATE INDEX idx_rides_status ON rides (status);
CREATE INDEX idx_rides_ride_type ON rides (ride_type);
CREATE INDEX idx_rides_outstation_trip_type ON rides (outstation_trip_type);
CREATE INDEX idx_rides_pickup_lat_lng ON rides (pickup_lat, pickup_lng);
CREATE INDEX idx_rides_drop_lat_lng ON rides (drop_lat, drop_lng);
CREATE INDEX idx_rides_created_at ON rides (created_at DESC);
CREATE INDEX idx_rides_booking_date ON rides (booking_date DESC);
-- getPendingRideAmounts (ride.service.js) rolls up unpaid cancellation dues per user
CREATE INDEX idx_rides_pending_cancel_dues ON rides (user_id, status, canceled_by, cancel_amount_paid, driver_id);
CREATE INDEX idx_rides_ignored_drivers ON rides USING GIN (ignored_drivers);
CREATE INDEX idx_rides_requested_drivers ON rides USING GIN (requested_drivers);

-- =====================================================================
-- ride_process  (ride.schema.js embedded "process[]" - RideStateSchema, the ride status timeline)
-- =====================================================================
CREATE TABLE ride_process (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    ride_id    UUID        NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ride_process_legacy_mongo_id ON ride_process (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_process_ride_id ON ride_process (ride_id, created_at);

-- =====================================================================
-- ride_admin_payments  (ride.schema.js embedded "adminPayment[]" - AdminPaymentSchema)
-- =====================================================================
CREATE TABLE ride_admin_payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    ride_id     UUID           NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    transaction JSONB          NOT NULL DEFAULT '{}'::jsonb,
    amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ride_admin_payments_legacy_mongo_id ON ride_admin_payments (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_admin_payments_ride_id ON ride_admin_payments (ride_id);

-- =====================================================================
-- ride_driver_payments  (ride.schema.js embedded "driverPayment[]" - DriverPaymentSchema)
-- =====================================================================
CREATE TABLE ride_driver_payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    ride_id     UUID           NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    transaction JSONB          NOT NULL DEFAULT '{}'::jsonb,
    amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ride_driver_payments_legacy_mongo_id ON ride_driver_payments (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_driver_payments_ride_id ON ride_driver_payments (ride_id);

-- =====================================================================
-- star_ratings  (modals/rating.schema.js, Mongoose collection "starratings")
-- =====================================================================
CREATE TABLE star_ratings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    star_rating NUMERIC(3, 2),
    reason      TEXT        NOT NULL DEFAULT '',
    -- user-service / driver-service own these ids - plain uuid, no FK
    user_id     UUID,
    driver_id   UUID,
    from_type   VARCHAR(10) NOT NULL DEFAULT '',
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    ride_id     UUID REFERENCES rides (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_star_ratings_legacy_mongo_id ON star_ratings (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
-- getRatingByRide (ride.service.js): findOne({ rideid, from, delete: { $ne: true } })
CREATE INDEX idx_star_ratings_ride_from_deleted ON star_ratings (ride_id, from_type, is_deleted);
CREATE INDEX idx_star_ratings_user_id ON star_ratings (user_id);
CREATE INDEX idx_star_ratings_driver_id ON star_ratings (driver_id);

-- =====================================================================
-- ride_activities  (modals/rideactivity.schema.js, Mongoose collection "Activity")
-- =====================================================================
CREATE TABLE ride_activities (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    ride_id    UUID        NOT NULL REFERENCES rides (id),
    action     VARCHAR(50) NOT NULL,
    ip         VARCHAR(64) NOT NULL DEFAULT '',
    device     VARCHAR(255) NOT NULL DEFAULT '',
    meta       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ride_activities_legacy_mongo_id ON ride_activities (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_activities_ride_id ON ride_activities (ride_id);
CREATE INDEX idx_ride_activities_created_at ON ride_activities (created_at);

-- =====================================================================
-- ride_gps  (modals/ridegps.schema.js, Mongoose collection "ridegps") - GPS trail points
-- =====================================================================
CREATE TABLE ride_gps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id VARCHAR(24),
    ride_id     UUID REFERENCES rides (id),
    -- driver-service / user-service own these ids - plain uuid, no FK
    driver_id   UUID,
    user_id     UUID,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    speed       DOUBLE PRECISION,
    heading     DOUBLE PRECISION,
    accuracy    DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ride_gps_legacy_mongo_id ON ride_gps (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_gps_ride_id_recorded_at ON ride_gps (ride_id, recorded_at);
CREATE INDEX idx_ride_gps_driver_id_recorded_at ON ride_gps (driver_id, recorded_at);

-- =====================================================================
-- ride_payments  (modals/ridePaymentSchema.js)
-- =====================================================================
CREATE TABLE ride_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id  VARCHAR(24),
    ride_id          UUID           NOT NULL REFERENCES rides (id),
    action           VARCHAR(30)    NOT NULL,
    payment_to       VARCHAR(10)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    is_claimed       BOOLEAN        NOT NULL DEFAULT FALSE,
    -- owned by user-service, driver-service or admin-service depending on payment_to - no FK
    payment_user_id  UUID,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_ride_payments_action CHECK (action IN ('driverfees', 'adminfees', 'driverreferralfees', 'userreferralfees')),
    CONSTRAINT chk_ride_payments_payment_to CHECK (payment_to IN ('user', 'driver', 'admin'))
);

CREATE UNIQUE INDEX uq_ride_payments_legacy_mongo_id ON ride_payments (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
CREATE INDEX idx_ride_payments_ride_id ON ride_payments (ride_id);
CREATE INDEX idx_ride_payments_payment_user_id ON ride_payments (payment_user_id);
CREATE INDEX idx_ride_payments_action ON ride_payments (action);
-- mirrors ridePaymentSchema's unique+partialFilterExpression index exactly
CREATE UNIQUE INDEX uq_ride_payments_referral_fees ON ride_payments (ride_id, action, payment_user_id) WHERE action = 'driverreferralfees';
