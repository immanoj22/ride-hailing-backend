package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.StarRating;

/** Mirrors addRatings/getRatingByRide in ride.service.js (modals/rating.schema.js, collection "starratings"). */
public interface StarRatingRepository extends JpaRepository<StarRating, UUID> {

    /** getRatingByRide: findOne({ rideid, from, delete: { $ne: true } }). */
    Optional<StarRating> findByRideIdAndFromTypeAndDeletedFalse(UUID rideId, String fromType);

    List<StarRating> findByDriverIdAndDeletedFalse(UUID driverId);

    List<StarRating> findByUserIdAndDeletedFalse(UUID userId);

    List<StarRating> findByRideId(UUID rideId);

    /** get_driver_rides: per-ride "driver" rating scoped to (ride, driver, user). */
    Optional<StarRating> findByRideIdAndDriverIdAndUserIdAndFromTypeAndDeletedFalse(
            UUID rideId, UUID driverId, UUID userId, String fromType);

    List<StarRating> findByRideIdInAndDeletedFalse(List<UUID> rideIds);
}
