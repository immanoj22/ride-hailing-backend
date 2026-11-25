package com.ridehail.rideservice.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.entity.StarRating;
import com.ridehail.rideservice.repository.StarRatingRepository;

/**
 * Port of ride.service.js addRatings / getRatingByRide and the thin ride.controller.js wrappers
 * starRatings / getRatingByRide (modals/rating.schema.js, collection "starratings").
 */
@Service
public class RatingService {

    private final StarRatingRepository starRatingRepository;

    public RatingService(StarRatingRepository starRatingRepository) {
        this.starRatingRepository = starRatingRepository;
    }

    /** ride.controller.js starRatings. */
    @Transactional
    public Map<String, Object> starRatings(java.math.BigDecimal starrating, String reason, String userid,
            String driverid, String from, String rideid) {
        StarRating rating = StarRating.builder()
                .starRating(starrating)
                .reason(reason == null ? "" : reason)
                .userId(parseUuid(userid))
                .driverId(parseUuid(driverid))
                .fromType(from == null ? "" : from)
                .rideId(parseUuid(rideid))
                .deleted(false)
                .build();
        StarRating saved = starRatingRepository.save(rating);
        return toLegacyMap(saved);
    }

    /** ride.controller.js getRatingByRide - returns null when nothing recorded yet. */
    @Transactional(readOnly = true)
    public Map<String, Object> getRatingByRide(String rideid, String from) {
        if (rideid == null || rideid.isBlank() || from == null || from.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Missing required fields");
        }
        Optional<StarRating> found = starRatingRepository
                .findByRideIdAndFromTypeAndDeletedFalse(parseUuid(rideid), from);
        return found.map(RatingService::toLegacyMap).orElse(null);
    }

    private static UUID parseUuid(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid id: " + v);
        }
    }

    /** Reproduces the Mongoose document shape (field names verbatim, "-__v" projection). */
    static Map<String, Object> toLegacyMap(StarRating r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", r.getId());
        m.put("starrating", r.getStarRating());
        m.put("reason", r.getReason());
        m.put("userid", r.getUserId());
        m.put("driverid", r.getDriverId());
        m.put("from", r.getFromType());
        m.put("delete", r.isDeleted());
        m.put("rideid", r.getRideId());
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }
}
