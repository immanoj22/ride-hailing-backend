-- socket-service initial schema
-- Mongo collections migrated: Chat (modals/socket.schema.js)
--
-- socket-service is primarily a real-time gateway (netty-socketio) - it has no other persisted
-- domain state of its own besides the chat/support message history exposed by
-- controller/socket.controller.js's REST endpoints.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================
-- chat_messages  (modals/socket.schema.js "Chat" model, collection "Chat")
-- =====================================================================
CREATE TABLE chat_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_mongo_id   VARCHAR(24),

    -- ride-service owns rides, user-service owns users, driver-service owns drivers, and
    -- helperdesk-service owns support tickets - all plain uuid columns with no FK, matching the
    -- original Mongoose ObjectId references (rideId is the only one actually declared on the
    -- schema; driverId/userId/ticketId are read by services/socket.services.js's query helpers
    -- even though nothing currently persists them - see ChatMessage entity javadoc).
    ride_id           UUID,
    sender_id         UUID,
    driver_id         UUID,
    user_id           UUID,
    ticket_id         UUID,

    from_type         VARCHAR(10) NOT NULL CHECK (from_type IN ('user', 'driver')),

    msg               TEXT NOT NULL DEFAULT '',
    file              TEXT[] NOT NULL DEFAULT '{}',
    seen              BOOLEAN NOT NULL DEFAULT FALSE,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_chat_messages_legacy_mongo_id ON chat_messages (legacy_mongo_id) WHERE legacy_mongo_id IS NOT NULL;
-- rideId/senderId both had index:true on the Mongoose schema.
CREATE INDEX idx_chat_messages_ride_id ON chat_messages (ride_id);
CREATE INDEX idx_chat_messages_sender_id ON chat_messages (sender_id);
-- Used by get_Ride_Message's $match/$group and get_chat_message/get_chat_from_user's find().
CREATE INDEX idx_chat_messages_driver_id ON chat_messages (driver_id);
CREATE INDEX idx_chat_messages_user_id ON chat_messages (user_id);
CREATE INDEX idx_chat_messages_ticket_id ON chat_messages (ticket_id);
-- Every query helper .sort({createdAt: ...}).
CREATE INDEX idx_chat_messages_created_at ON chat_messages (created_at);
