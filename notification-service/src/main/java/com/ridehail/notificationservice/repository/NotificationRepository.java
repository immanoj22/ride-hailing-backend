package com.ridehail.notificationservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.notificationservice.entity.Notification;

/**
 * Backs service/notification.servie.js's saveNotification / findOneNotification /
 * findAllNotification helpers, and the ad-hoc queries built in
 * controllers/notification.controller.js's getNotification / getAllNotification.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** getAllNotification: Notification.find({}).sort({createdAt:-1}) with optional paging. */
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** getNotification (no req.user/req.driver branch): query.Whom = user (query param). */
    Page<Notification> findByWhomOrderByCreatedAtDesc(String whom, Pageable pageable);

    /**
     * getNotification's $or for an authenticated user:
     * [{Whom:"user"}, {Whom:"selectedUser", mobile: regex fcm}, {userIds: user._id}].
     * user_ids is a native uuid[] column (not a JPA element collection), so containment has to
     * be a native query using Postgres' ANY(array) operator - a derived "...Containing" method
     * would not work against a raw array-typed attribute.
     */
    @Query(value = "SELECT * FROM notifications n "
            + "WHERE n.whom = :whom "
            + "OR (n.whom = 'selectedUser' AND n.mobile LIKE CONCAT('%', :fcm, '%')) "
            + "OR (:userId IS NOT NULL AND :userId = ANY(n.user_ids)) "
            + "ORDER BY n.created_at DESC",
            nativeQuery = true)
    List<Notification> findForUser(@Param("whom") String whom, @Param("fcm") String fcm, @Param("userId") UUID userId);

    /** getNotification's $or for an authenticated driver, mirroring the user variant above. */
    @Query(value = "SELECT * FROM notifications n "
            + "WHERE n.whom = :whom "
            + "OR (n.whom = 'selectedDriver' AND n.mobile LIKE CONCAT('%', :fcmToken, '%')) "
            + "OR (:driverId IS NOT NULL AND :driverId = ANY(n.driver_ids)) "
            + "ORDER BY n.created_at DESC",
            nativeQuery = true)
    List<Notification> findForDriver(@Param("whom") String whom, @Param("fcmToken") String fcmToken, @Param("driverId") UUID driverId);
}
