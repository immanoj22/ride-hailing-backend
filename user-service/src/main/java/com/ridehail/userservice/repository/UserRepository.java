package com.ridehail.userservice.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ridehail.userservice.entity.User;

/**
 * Backs service/user.services.js's findOneUser/updateUser/getUsers/getUserCount/etc.
 * JpaSpecificationExecutor supports the dynamic search/date-range/isBlocked filtering used by
 * get_users_list / get_users_ride_report / export_report in user.controller.js.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByMobile(String mobile);

    Optional<User> findByEmail(String email);

    Optional<User> findByReferralCode(String referralCode);

    List<User> findByMobileIn(Collection<String> mobiles);

    /**
     * Row-level lock used by the wallet-referral-rewards bucket to reproduce Mongoose's atomic
     * {@code findOneAndUpdate} semantics (e.g. the "only apply a referral once" guard in
     * user.controller.js#applyUserReferralReward and the trip-discount consumption guard in
     * #consume_referral_discount) inside a single Postgres transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Atomically adjusts users.wallet_current_amount, reproducing service/user.services.js's
     * updateUserWallet overdraft guard: a debit (negative amount) is only applied when the
     * resulting balance would stay &gt;= 0, matching the original's
     * {@code "wallet.currentAmount": { $gte: Math.abs(incAmount) } } conditional filter. Returns
     * the number of rows updated (0 means "not found" or "insufficient balance", both of which the
     * legacy code treated identically as a failed update).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET wallet_current_amount = wallet_current_amount + :amount, "
            + "wallet_last_updated = now(), updated_at = now() "
            + "WHERE id = :id AND wallet_current_amount + :amount >= 0", nativeQuery = true)
    int adjustWalletCurrentAmount(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Atomically adjusts the separate legacy users.walletBalance field used only by
     * update_user_wallet_balance's bulkWrite (distinct from wallet.currentAmount above - the
     * original Node code never reconciled the two fields, and this port preserves that).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET wallet_balance = wallet_balance + :amount, updated_at = now() "
            + "WHERE id = :id", nativeQuery = true)
    int incrementWalletBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Backs user.controller.js#get_users_report's single $group aggregate (totalUsers/deletedUsers/
     * newUsers/activeUsers over the whole `users` collection, no match filter).
     */
    @Query(value = """
            SELECT COUNT(*) AS "totalUsers",
                   COUNT(*) FILTER (WHERE is_deleted = true) AS "deletedUsers",
                   COUNT(*) FILTER (WHERE is_deleted = false AND created_at >= :fromDate AND created_at <= :toDate) AS "newUsers",
                   COUNT(*) FILTER (WHERE is_deleted = false AND jsonb_array_length(trip_ids) > 0) AS "activeUsers"
            FROM users
            """, nativeQuery = true)
    UsersReportProjection getUsersReportStats(@Param("fromDate") OffsetDateTime fromDate, @Param("toDate") OffsetDateTime toDate);

    /** Projection backing {@link #getUsersReportStats}. */
    interface UsersReportProjection {
        Long getTotalUsers();

        Long getDeletedUsers();

        Long getNewUsers();

        Long getActiveUsers();
    }
}
