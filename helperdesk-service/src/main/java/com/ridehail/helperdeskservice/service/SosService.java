package com.ridehail.helperdeskservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.realtime.SocketRelayPublisher;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.helperdeskservice.dto.ListSosRequest;
import com.ridehail.helperdeskservice.dto.SosResponse;
import com.ridehail.helperdeskservice.dto.TriggerSosRequest;
import com.ridehail.helperdeskservice.entity.SosAlert;
import com.ridehail.helperdeskservice.repository.SosAlertRepository;

/**
 * "sos-alerts" bucket - direct port of helperdesk.controller.js's triggerSOS / listSOS
 * (+ helperdesk.services.js's sosSave/findOneSOS/findSOS/getSOSCount).
 */
@Service
@Transactional(readOnly = true)
public class SosService {

    private static final String SOS_TRIGGERED_EVENT = "sos:triggered"; // helperdesk.constants.js SOCKET_KEYS

    private final SosAlertRepository sosAlertRepository;
    private final InternalServiceClient internalServiceClient;
    private final SocketRelayPublisher socketRelayPublisher;
    private final String rideServiceUrl;
    private final String userServiceUrl;
    private final String driverServiceUrl;

    public SosService(
            SosAlertRepository sosAlertRepository,
            InternalServiceClient internalServiceClient,
            SocketRelayPublisher socketRelayPublisher,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl) {
        this.sosAlertRepository = sosAlertRepository;
        this.internalServiceClient = internalServiceClient;
        this.socketRelayPublisher = socketRelayPublisher;
        this.rideServiceUrl = rideServiceUrl;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
    }

    @Transactional
    public SosResponse triggerSos(TriggerSosRequest request) {
        UUID rideId = parseId(request.getRideId(), "rideId");
        UUID userId = parseId(request.getUserId(), "userId");
        String userType = request.getUserType();

        JsonNode rideResp = internalServiceClient.get(rideServiceUrl + "/rides/" + rideId);
        if (rideResp == null || !rideResp.path("status").asBoolean(false)) {
            throw new ApiException(StatusCode.NOT_FOUND, "Ride not found");
        }
        JsonNode ride = rideResp.path("data").path("ride");

        if ("rider".equals(userType)) {
            String rideUserId = ride.path("userId").asText(ride.path("_id").asText(""));
            if (rideUserId.isEmpty() || !rideUserId.equals(request.getUserId())) {
                throw new ApiException(StatusCode.FORBIDDEN, "Rider does not belong to this ride");
            }
        }
        if ("driver".equals(userType)) {
            JsonNode rideDriverId = ride.path("driverId");
            if (rideDriverId.isMissingNode() || rideDriverId.isNull() || rideDriverId.asText("").isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Driver not assigned to this ride");
            }
            if (!rideDriverId.asText().equals(request.getUserId())) {
                throw new ApiException(StatusCode.FORBIDDEN, "Driver does not belong to this ride");
            }
        }

        sosAlertRepository.findByRideIdAndUserIdAndUserType(rideId, userId, userType).ifPresent(existing -> {
            throw new ApiException(StatusCode.CONFLICT, "An active SOS already exists for this ride");
        });

        SosAlert sos = SosAlert.builder()
                .rideId(rideId)
                .userId(userId)
                .userType(userType)
                .locationLat(request.getLocation().getLat())
                .locationLng(request.getLocation().getLng())
                .build();
        sos = sosAlertRepository.save(sos);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sosId", sos.getId());
        payload.put("rideId", rideId);
        payload.put("userId", userId);
        payload.put("userType", userType);
        payload.put("location", Map.of("lat", request.getLocation().getLat(), "lng", request.getLocation().getLng()));
        payload.put("triggeredAt", sos.getCreatedAt());
        socketRelayPublisher.toAdmins(SOS_TRIGGERED_EVENT, payload);

        return SosResponse.from(sos, null, null);
    }

    public Map<String, Object> listSos(ListSosRequest request) {
        int page = request.getPage() == null ? 1 : request.getPage();
        int limit = request.getLimit() == null ? 20 : request.getLimit();
        int skip = (page - 1) * limit;

        if (request.getSosId() != null && !request.getSosId().isBlank()) {
            SosAlert sos = sosAlertRepository.findById(parseId(request.getSosId(), "sosId"))
                    .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "SOS not found"));
            SosResponse enriched = enrich(sos);
            return Map.of("list", enriched, "totalPages", 1);
        }

        List<SosAlert> all;
        long total;
        if (request.getUserType() != null && !request.getUserType().isBlank()) {
            var pageResult = sosAlertRepository.findByUserType(request.getUserType(),
                    PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "createdAt")));
            all = pageResult.getContent();
            total = pageResult.getTotalElements();
        } else {
            all = sosAlertRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            total = all.size();
        }

        List<SosResponse> enriched = new ArrayList<>();
        for (SosAlert sos : all) {
            enriched.add(enrich(sos));
        }

        String search = request.getSearch();
        if (search != null && !search.isBlank()) {
            String needle = search.toLowerCase().replaceAll("\\s+", " ").trim();
            enriched = enriched.stream().filter(s -> matchesSearch(s, needle)).toList();
            total = enriched.size();
            int to = Math.min(skip + limit, enriched.size());
            enriched = skip >= enriched.size() ? List.of() : enriched.subList(skip, to);
        } else {
            int to = Math.min(skip + limit, enriched.size());
            enriched = skip >= enriched.size() ? List.of() : enriched.subList(skip, to);
        }

        if (enriched.isEmpty() && total == 0) {
            throw new ApiException(StatusCode.NOT_FOUND, "SOS not found");
        }

        return Map.of("list", enriched, "totalPages", (int) Math.ceil((double) total / limit));
    }

    private SosResponse enrich(SosAlert sos) {
        JsonNode user = null;
        JsonNode driver = null;
        if ("rider".equals(sos.getUserType()) && sos.getUserId() != null) {
            JsonNode resp = internalServiceClient.get(userServiceUrl + "/profile/" + sos.getUserId());
            user = resp == null ? null : resp.path("data").path("user");
        } else if ("driver".equals(sos.getUserType()) && sos.getUserId() != null) {
            JsonNode resp = internalServiceClient.get(driverServiceUrl + "/profile/" + sos.getUserId());
            driver = resp == null ? null : resp.path("data");
        }
        return SosResponse.from(sos, isEmpty(user) ? null : user, isEmpty(driver) ? null : driver);
    }

    private boolean matchesSearch(SosResponse s, String needle) {
        String userName = s.user() == null ? "" : firstNonBlank(
                s.user().path("fullname").asText(""), s.user().path("firstname").asText("")).toLowerCase();
        String driverName = s.driver() == null ? "" : (s.driver().path("firstName").asText("") + " "
                + s.driver().path("lastName").asText("")).toLowerCase().trim();
        return userName.contains(needle) || driverName.contains(needle)
                || (s.userId() != null && s.userId().toString().contains(needle));
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private boolean isEmpty(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                || (node.isObject() && node.isEmpty()) || (node.isTextual() && node.asText().isBlank());
    }

    private UUID parseId(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid " + field);
        }
    }
}
