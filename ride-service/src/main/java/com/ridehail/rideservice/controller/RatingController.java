package com.ridehail.rideservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.constants.StatusResult;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.dto.AddRatingsRequest;
import com.ridehail.rideservice.service.RatingService;

import jakarta.validation.Valid;

/** Port of ride.controller.js starRatings / getRatingByRide (routes/ride.routes.js, prefix /ride-apis). */
@RestController
@RequestMapping("/ride-apis")
public class RatingController {

    private final RatingService ratingService;
    private final ApiResponseService apiResponseService;

    public RatingController(RatingService ratingService, ApiResponseService apiResponseService) {
        this.ratingService = ratingService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/starRatings")
    public ResponseEntity<?> starRatings(@Valid @RequestBody AddRatingsRequest body) {
        Map<String, Object> rating = ratingService.starRatings(body.getStarrating(), body.getReason(),
                body.getUserid(), body.getDriverid(), body.getFrom(), body.getRideid());
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.CREATED, "Star Ratings created", rating);
    }

    @GetMapping("/get-rating")
    public ResponseEntity<?> getRatingByRide(@RequestParam(value = "rideid", required = false) String rideid,
            @RequestParam(value = "from", required = false) String from) {
        Map<String, Object> rating = ratingService.getRatingByRide(rideid, from);
        if (rating == null) {
            return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, "No rating applied yet", null);
        }
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, "Rating details fetched", rating);
    }
}
