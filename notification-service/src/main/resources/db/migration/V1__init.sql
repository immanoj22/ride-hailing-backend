-- notification-service initial schema
-- Mirrors modal/notification.schema.js (Notification) and modal/emailTemplate.shema.js (EmailTemplate).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================================
-- notifications  (modal/notification.schema.js)
-- =====================================================================================
CREATE TABLE notifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id  VARCHAR(24),

    image            TEXT NOT NULL DEFAULT '',
    title            VARCHAR(255) NOT NULL DEFAULT '',
    message          TEXT NOT NULL DEFAULT '',
    body             TEXT NOT NULL DEFAULT '',
    -- "Whom" in the original schema: free-form string, values used by the app are
    -- "user" / "driver" / "selectedUser" / "selectedDriver" but no Mongoose enum was declared,
    -- so no CHECK constraint is added here (preserve original permissiveness).
    whom             VARCHAR(20) NOT NULL DEFAULT '',
    -- Legacy field: despite the name, the app actually stores a JSON-stringified array of FCM
    -- tokens here (see controllers/notification.controller.js pushNotification) - kept as TEXT
    -- to preserve behavior exactly, not renamed/fixed.
    mobile           TEXT NOT NULL DEFAULT '',
    coupons          VARCHAR(255) NOT NULL DEFAULT '',

    -- userIds / driverIds: Mongoose arrays of ObjectId ref User / Driver. Those services are
    -- owned by user-service / driver-service respectively, so these are plain uuid[] columns
    -- with no foreign key / join - resolve via InternalServiceClient when actual records are
    -- needed, exactly like the original .populate()-free axios lookups did.
    user_ids         UUID[] NOT NULL DEFAULT '{}',
    driver_ids       UUID[] NOT NULL DEFAULT '{}',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notifications_legacy_mongo_id ON notifications (legacy_mongo_id);
-- getNotification / getAllNotification: Notification.find(query).sort({createdAt:-1}).skip().limit()
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
-- getNotification builds an $or on Whom (plain "user"/"driver" branch) and getAllNotification
-- filters on Whom too
CREATE INDEX idx_notifications_whom ON notifications (whom);
-- getNotification's $or also matches { userIds: req.user._id } / { driverIds: req.driver._id }
CREATE INDEX idx_notifications_user_ids ON notifications USING GIN (user_ids);
CREATE INDEX idx_notifications_driver_ids ON notifications USING GIN (driver_ids);

-- =====================================================================================
-- email_templates  (modal/emailTemplate.shema.js, collection "emailTemplate")
-- =====================================================================================
CREATE TABLE email_templates (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id  VARCHAR(24),

    subject          VARCHAR(255) NOT NULL DEFAULT '',
    content          TEXT NOT NULL DEFAULT '',
    -- Not declared unique:true in the Mongoose schema, but service/notification.controller.js's
    -- add_mail always checks getMailTemplate({key}) for an existing row before creating one
    -- (rejects with "Key already exist"), i.e. key is treated as unique by every code path that
    -- writes it - enforced here at the DB layer too.
    key              VARCHAR(50) NOT NULL DEFAULT '',
    status           VARCHAR(20) NOT NULL DEFAULT 'active',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_email_templates_legacy_mongo_id ON email_templates (legacy_mongo_id);
-- getMailTemplate({key}) / add_mail's pre-existence check / edit_mail's lookup by key
CREATE UNIQUE INDEX uq_email_templates_key ON email_templates (key);
