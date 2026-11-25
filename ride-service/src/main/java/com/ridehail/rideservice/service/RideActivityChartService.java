package com.ridehail.rideservice.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.repository.RideRepository;

/** Ports ride.controller.js: get_ride_activity_chart + ride.service.js getRideActivity. */
@Service
@Transactional(readOnly = true)
public class RideActivityChartService {

    private static final List<String> COMPLETED = List.of("endtrip", "completepayment", "dropoff");
    private static final List<String> CANCELLED = List.of("cancel", "driver_cancel", "user_cancel", "timeout", "ignored");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RideRepository rideRepository;

    public RideActivityChartService(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    public Map<String, Object> getRideActivityChart(String from, String to) {
        OffsetDateTime parsedFrom = parse(from, "Invalid 'from' date");
        OffsetDateTime parsedTo = parse(to, "Invalid 'to' date");

        if (parsedFrom == null && parsedTo == null) {
            parsedFrom = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        }

        OffsetDateTime seriesFrom = parsedFrom != null ? parsedFrom : OffsetDateTime.now(ZoneOffset.UTC).minusYears(50);
        OffsetDateTime seriesTo = parsedTo != null ? parsedTo : OffsetDateTime.now(ZoneOffset.UTC).plusYears(50);

        List<Ride> rides = rideRepository.findByDeletedFalseAndCreatedAtBetween(seriesFrom, seriesTo);

        TreeMap<String, long[]> byDate = new TreeMap<>();
        for (Ride r : rides) {
            String d = r.getCreatedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate().format(DAY);
            long[] c = byDate.computeIfAbsent(d, k -> new long[3]);
            c[0]++;
            if (COMPLETED.contains(r.getStatus())) {
                c[1]++;
            }
            if (CANCELLED.contains(r.getStatus())) {
                c[2]++;
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();
        byDate.forEach((d, c) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            row.put("total", c[0]);
            row.put("completed", c[1]);
            row.put("cancelled", c[2]);
            series.add(row);
        });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("today", countSummary(startOfToday));
        summary.put("last7Days", countSummary(sevenDaysAgo));
        summary.put("last30Days", countSummary(thirtyDaysAgo));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series", series);
        data.put("summary", summary);
        return data;
    }

    private Map<String, Object> countSummary(OffsetDateTime start) {
        long total = rideRepository.countByDeletedFalseAndCreatedAtGreaterThanEqual(start);
        long completed = rideRepository.countByDeletedFalseAndStatusInAndCreatedAtGreaterThanEqual(COMPLETED, start);
        long cancelled = rideRepository.countByDeletedFalseAndStatusInAndCreatedAtGreaterThanEqual(CANCELLED, start);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("completed", completed);
        m.put("cancelled", cancelled);
        return m;
    }

    private OffsetDateTime parse(String value, String errMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(value);
        } catch (Exception e1) {
            try {
                return java.time.Instant.parse(value).atOffset(ZoneOffset.UTC);
            } catch (Exception e2) {
                throw new ApiException(StatusCode.BAD_REQUEST, errMessage);
            }
        }
    }
}
