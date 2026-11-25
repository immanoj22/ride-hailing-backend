package com.ridehail.socketservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.socketservice.entity.ChatMessage;

/**
 * Backs services/socket.services.js's Chat query helpers (add_Message, get_Ride_Message,
 * get_chat_message, get_chat_from_user, findRides).
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** findRides({ query: { rideId } }) / getMessageByRideId. */
    List<ChatMessage> findByRideIdOrderByCreatedAtAsc(UUID rideId);

    /** get_chat_message({ ticketId }) - used by getChatUsingTicketId. */
    List<ChatMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    /** get_chat_from_user when neither rideId nor driverId is supplied (direct admin&lt;-&gt;user
     *  chat: userId + rideId IS NULL + from in [admin, user]). */
    List<ChatMessage> findByUserIdAndRideIdIsNullAndFromInOrderByCreatedAtAsc(UUID userId, List<String> from);

    /** get_chat_from_user when both rideId and driverId are supplied (ride chat: userId +
     *  driverId + rideId + from in [user, driver]). */
    List<ChatMessage> findByUserIdAndDriverIdAndRideIdAndFromInOrderByCreatedAtAsc(
            UUID userId, UUID driverId, UUID rideId, List<String> from);

    /**
     * get_Ride_Message: for a given ride, the latest message per driver among from in
     * [driver, admin], equivalent to the original's
     * {@code $match -> $sort(createdAt desc) -> $group(_id: driverId, $first: ...)} aggregation.
     */
    @Query(value = """
            SELECT DISTINCT ON (driver_id) *
            FROM chat_messages
            WHERE ride_id = :rideId
              AND driver_id IS NOT NULL
              AND from_type IN ('driver', 'admin')
            ORDER BY driver_id, created_at DESC
            """, nativeQuery = true)
    List<ChatMessage> findLatestMessagePerDriverForRide(@Param("rideId") UUID rideId);
}
