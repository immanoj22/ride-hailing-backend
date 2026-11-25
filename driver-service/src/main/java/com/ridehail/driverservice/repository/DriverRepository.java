package com.ridehail.driverservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.driverservice.entity.Driver;

/**
 * Equivalent of service/driver.services.js's findOneDriver/findDrivers/getDriverCount/etc.
 *
 * <p>Highly dynamic filters (nearby_drivers, get_drivers_ride_report, drivers_kyc_list,
 * export_report - combining optional searchText/status/gender/date-range filters) are built with
 * the {@link JpaSpecificationExecutor} this repository now also extends (see
 * {@code com.ridehail.driverservice.service.DriverSpecifications} in the implementation step) rather
 * than exploding into dozens of derived-method combinations here.
 */
public interface DriverRepository extends JpaRepository<Driver, UUID>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByMobile(String mobile);

    Optional<Driver> findByMobileAndDeletedFalse(String mobile);

    Optional<Driver> findByEmail(String email);

    Optional<Driver> findByReferralCode(String referralCode);

    List<Driver> findByIdIn(List<UUID> ids);

    List<Driver> findByIdInAndDeletedFalse(List<UUID> ids);

    /** Equivalent of the legacy denormalized "referrals" array - drivers referred by this driver. */
    List<Driver> findByReferredBy_IdAndDeletedFalse(UUID referredById);

    long countByReferredBy_IdAndDeletedFalse(UUID referredById);

    long countByDeletedFalse();

    Page<Driver> findByDeletedFalse(Pageable pageable);

    Page<Driver> findByDriverStatusAndDeletedFalse(String driverStatus, Pageable pageable);

    Page<Driver> findByUnverifiedDriverTrueAndDeletedFalse(Pageable pageable);

    Page<Driver> findByKycStatusAndDeletedFalse(String kycStatus, Pageable pageable);

    @Query("""
            select d from Driver d
            where d.deleted = false
              and (lower(d.firstName) like lower(concat('%', :name, '%'))
                   or lower(d.lastName) like lower(concat('%', :name, '%')))
            """)
    List<Driver> searchByName(@Param("name") String name);

    /** getDriverForNotification's default query: {fcmToken: {$exists:true, $nin:[null,""]}}. */
    @Query("select d from Driver d where d.fcmToken <> ''")
    List<Driver> findAllWithFcmToken();

    @Query("select d from Driver d where d.fcmToken <> '' and d.mobile in :mobiles")
    List<Driver> findWithFcmTokenAndMobileIn(@Param("mobiles") List<String> mobiles);

    /** getDriverForNotification's action=="all" query: {mobile: {$ne:null}, delete:false}. */
    List<Driver> findByMobileIsNotNullAndDeletedFalse();

    /**
     * get_drivers_report's "activeDrivers" metric: {@code $gt: [{$size: {$ifNull: ["$trips", []]}}, 0]}
     * combined with {@code delete: false} - i.e. drivers with at least one recent trip id.
     */
    @Query(value = "select count(*) from drivers where is_deleted = false and cardinality(recent_trip_ids) > 0",
            nativeQuery = true)
    long countActiveDrivers();
}
