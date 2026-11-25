package com.ridehail.driverservice.service;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverDocument;

import jakarta.persistence.criteria.Subquery;

/**
 * Builds the dynamic filter combinations used by getDriversStatus / get_drivers_ride_report /
 * drivers_kyc_list / export_report (each combining a different subset of optional filters in the
 * legacy Mongoose queries) as composable JPA {@link Specification}s.
 */
public final class DriverSpecifications {

    private DriverSpecifications() {
    }

    public static Specification<Driver> deletedEq(boolean deleted) {
        return (root, query, cb) -> cb.equal(root.get("deleted"), deleted);
    }

    public static Specification<Driver> driverStatusEq(String status) {
        return (root, query, cb) -> cb.equal(root.get("driverStatus"), status);
    }

    public static Specification<Driver> kycStatusEq(String status) {
        return (root, query, cb) -> cb.equal(root.get("kycStatus"), status);
    }

    public static Specification<Driver> kycCompletedEq(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("kycCompleted"), value);
    }

    public static Specification<Driver> unverifiedDriverEq(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("unverifiedDriver"), value);
    }

    public static Specification<Driver> genderEq(String gender) {
        return (root, query, cb) -> cb.equal(root.get("gender"), gender);
    }

    public static Specification<Driver> vehicleTypeEq(UUID vehicleType) {
        return (root, query, cb) -> cb.equal(root.get("vehicleType"), vehicleType);
    }

    public static Specification<Driver> vehicleVariantEq(UUID vehicleVariant) {
        return (root, query, cb) -> cb.equal(root.get("vehicleVariant"), vehicleVariant);
    }

    public static Specification<Driver> idEq(UUID id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Driver> onRideEq(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("onRide"), value);
    }

    public static Specification<Driver> latBetween(double min, double max) {
        return (root, query, cb) -> cb.between(root.get("currentLatitude"), min, max);
    }

    public static Specification<Driver> lngBetween(double min, double max) {
        return (root, query, cb) -> cb.between(root.get("currentLongitude"), min, max);
    }

    /** Equivalent of nearby_drivers/nearby_outstation_drivers requiring a resolved location. */
    public static Specification<Driver> hasLocation() {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("currentLatitude")), cb.isNotNull(root.get("currentLongitude")));
    }

    /** searchDrivers only matches firstName/lastName (unlike get_drivers_ride_report's wider search). */
    public static Specification<Driver> nameSearch(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern));
    }

    public static Specification<Driver> searchText(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(root.get("mobile")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("vehicleNumber")), pattern));
    }

    /** Equivalent of {@code documents: {$elemMatch: {status}}} - at least one child row with status. */
    public static Specification<Driver> hasDocumentWithStatus(String status) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var docRoot = sub.from(DriverDocument.class);
            sub.select(docRoot.get("id"));
            sub.where(cb.equal(docRoot.get("driver"), root), cb.equal(docRoot.get("status"), status));
            return cb.exists(sub);
        };
    }

    /** Equivalent of {@code documents: {$size: 0}}. */
    public static Specification<Driver> hasNoDocuments() {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var docRoot = sub.from(DriverDocument.class);
            sub.select(docRoot.get("id"));
            sub.where(cb.equal(docRoot.get("driver"), root));
            return cb.not(cb.exists(sub));
        };
    }

    public static Specification<Driver> alwaysTrue() {
        return (root, query, cb) -> cb.conjunction();
    }
}
