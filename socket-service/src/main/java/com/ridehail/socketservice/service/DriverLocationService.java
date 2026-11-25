package com.ridehail.socketservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Port of helper/socket.driverLocationService.js - raw Redis GEO commands over
 * {@code drivers:locations} plus a per-driver {@code driver:<id>:info} hash with a 300s TTL.
 */
@Service
public class DriverLocationService {

    private static final Logger log = LoggerFactory.getLogger(DriverLocationService.class);
    private static final String GEO_KEY = "drivers:locations";

    private final StringRedisTemplate redisTemplate;

    public DriverLocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean updateDriverLocation(String driverId, double latitude, double longitude) {
        try {
            redisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), driverId);

            String infoKey = "driver:" + driverId + ":info";
            Map<String, String> info = new LinkedHashMap<>();
            info.put("lat", String.valueOf(latitude));
            info.put("lng", String.valueOf(longitude));
            info.put("lastUpdate", String.valueOf(System.currentTimeMillis()));
            redisTemplate.opsForHash().putAll(infoKey, info);
            redisTemplate.expire(infoKey, 300, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Error updating driver location", e);
            return false;
        }
    }

    public List<Map<String, Object>> findNearbyDrivers(double latitude, double longitude) {
        return findNearbyDrivers(latitude, longitude, 5, 20);
    }

    public List<Map<String, Object>> findNearbyDrivers(double latitude, double longitude, double radiusKm, int count) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            GeoSearchCommandArgs args = GeoSearchCommandArgs.newGeoSearchArgs()
                    .includeCoordinates()
                    .includeDistance()
                    .sortAscending()
                    .limit(count);

            GeoResults<GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                    GEO_KEY,
                    GeoReference.fromCoordinate(longitude, latitude),
                    GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS)),
                    args);

            if (results == null) {
                return result;
            }
            for (GeoResult<GeoLocation<String>> r : results) {
                GeoLocation<String> loc = r.getContent();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("driverId", loc.getName());
                entry.put("distance", r.getDistance() == null ? null : r.getDistance().getValue());
                Map<String, Object> coords = new LinkedHashMap<>();
                Point p = loc.getPoint();
                coords.put("longitude", p == null ? null : p.getX());
                coords.put("latitude", p == null ? null : p.getY());
                entry.put("coordinates", coords);
                result.add(entry);
            }
        } catch (Exception e) {
            log.error("Error finding nearby drivers", e);
        }
        return result;
    }
}
