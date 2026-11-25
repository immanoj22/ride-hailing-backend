package com.ridehail.driverservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.driverservice.dto.DriverProjectionResponse;
import com.ridehail.driverservice.dto.DriverResponse;
import com.ridehail.driverservice.dto.DriversKycListRequest;
import com.ridehail.driverservice.dto.GetMultipleDriversByIdRequest;
import com.ridehail.driverservice.dto.KycVerifyRequest;
import com.ridehail.driverservice.dto.UpdateRideHailDriverRequest;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverDocument;
import com.ridehail.driverservice.repository.DriverDocumentRepository;
import com.ridehail.driverservice.repository.DriverRepository;

/**
 * Direct port of driver.controller.js's getDriverById / getDriverDetails / getDriversStatus /
 * getMultipleDriversById / searchDrivers / getUnverifiedDrivers / kyc_verify / drivers_kyc_list /
 * getDrivers / update_ridehailDrivers / get_profiles / update_driver_trip (+ their
 * service/driver.services.js helpers).
 */
@Service
public class DriverDirectoryService {

    private static final Set<String> PENDING_DOC_STATUSES = Set.of("suspended", "pending", "rejected");
    private static final Set<String> ALLOWED_KYC_STATUSES = Set.of("pending", "approved", "rejected", "suspended");
    private static final Set<String> DRIVER_TRIP_STATUSES = Set.of("rideaccept", "pickup", "startride", "endtrip");

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverResponseMapper driverResponseMapper;
    private final InternalServiceClient internalServiceClient;
    private final String vehicleServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DriverDirectoryService(
            DriverRepository driverRepository,
            DriverDocumentRepository driverDocumentRepository,
            DriverResponseMapper driverResponseMapper,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.vehicle-url}") String vehicleServiceUrl) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.driverResponseMapper = driverResponseMapper;
        this.internalServiceClient = internalServiceClient;
        this.vehicleServiceUrl = vehicleServiceUrl;
    }

    // ------------------------------------------------------------------
    // getDriverById
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getDriverById(String id) {
        UUID driverId = requireUuid(id, "Driver ID Required");
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(404, "Driver Not Found"));

        DriverResponse response = driverResponseMapper.toResponseWithDocsAndContacts(driver);
        return withVehicleDetails(response, driver.getVehicleType(), driver.getVehicleVariant());
    }

    // ------------------------------------------------------------------
    // getDriverDetails
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getDriverDetails(String driverId) {
        UUID id = requireUuid(driverId, "Driver ID Required");
        Driver driver = driverRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(404, "Driver Not Found"));

        DriverResponse response = driverResponseMapper.toFullResponse(driver);
        return withVehicleDetails(response, driver.getVehicleType(), driver.getVehicleVariant());
    }

    private Map<String, Object> withVehicleDetails(DriverResponse response, UUID vehicleType, UUID vehicleVariant) {
        @SuppressWarnings("unchecked")
        Map<String, Object> driverMap = objectMapper.convertValue(response, Map.class);
        JsonNode priceList = internalServiceClient.get(vehicleServiceUrl + "/price-list?vehicleTypeId="
                + (vehicleType == null ? "" : vehicleType) + "&vehicleVariantId="
                + (vehicleVariant == null ? "" : vehicleVariant));
        driverMap.put("vehicleDetails", priceList.isArray() ? objectMapper.convertValue(priceList, List.class) : List.of());
        return driverMap;
    }

    // ------------------------------------------------------------------
    // getDriversStatus
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverResponse> getDriversStatus(int page, int limit, String status, String driverStatus) {
        Specification<Driver> spec = Specification.where(DriverSpecifications.deletedEq(false));
        if (driverStatus != null && !driverStatus.isBlank()) {
            spec = spec.and(DriverSpecifications.driverStatusEq(driverStatus));
        }
        if ("pending".equals(status)) {
            spec = spec.and(DriverSpecifications.kycCompletedEq(false))
                    .and(DriverSpecifications.hasDocumentWithStatus("pending"));
        } else if ("verified".equals(status)) {
            spec = spec.and(DriverSpecifications.kycCompletedEq(true));
        } else if ("noitems".equals(status)) {
            spec = spec.and(DriverSpecifications.kycCompletedEq(false)).and(DriverSpecifications.hasNoDocuments());
        }

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit > 0 ? limit : 10);
        return driverRepository.findAll(spec, pageable).getContent().stream()
                .map(driverResponseMapper::toResponseWithDocsAndContacts)
                .toList();
    }

    // ------------------------------------------------------------------
    // searchDrivers
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverResponse> searchDrivers(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(400, "Search Name Required");
        }
        return driverRepository.searchByName(name.trim()).stream()
                .map(driverResponseMapper::toResponseWithDocsAndContacts)
                .toList();
    }

    // ------------------------------------------------------------------
    // getUnverifiedDrivers
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverResponse> getUnverifiedDrivers(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit > 0 ? limit : 10);
        return driverRepository.findByUnverifiedDriverTrueAndDeletedFalse(pageable).getContent().stream()
                .map(driverResponseMapper::toResponseWithDocsAndContacts)
                .toList();
    }

    // ------------------------------------------------------------------
    // getMultipleDriversById
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverProjectionResponse> getMultipleDriversById(GetMultipleDriversByIdRequest request) {
        List<UUID> ids = parseDriverIds(request == null ? null : request.getDriverIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        return driverRepository.findByIdIn(ids).stream()
                .map(this::toMultipleDriversProjection)
                .toList();
    }

    private DriverProjectionResponse toMultipleDriversProjection(Driver d) {
        DriverProjectionResponse response = new DriverProjectionResponse();
        response.setId(d.getId().toString());
        response.setFirstName(d.getFirstName());
        response.setLastName(d.getLastName());
        response.setEmail(d.getEmail());
        response.setMobile(d.getMobile());
        response.setProfileImage(d.getProfileImage());
        response.setVehicleNumber(d.getVehicleNumber());
        response.setFcmToken(d.getFcmToken());
        return response;
    }

    // ------------------------------------------------------------------
    // get_profiles
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverProjectionResponse> getProfiles(Object driverIdsParam) {
        List<UUID> ids = parseDriverIds(driverIdsParam);
        if (ids.isEmpty()) {
            throw new ApiException(400, "driverIds array is required");
        }
        List<Driver> drivers = driverRepository.findByIdIn(ids);
        if (drivers.isEmpty()) {
            throw new ApiException(404, "Drivers not found");
        }
        return drivers.stream().map(this::toProfileProjection).toList();
    }

    private DriverProjectionResponse toProfileProjection(Driver d) {
        DriverProjectionResponse response = new DriverProjectionResponse();
        response.setFirstName(d.getFirstName());
        response.setLastName(d.getLastName());
        response.setProfileImage(d.getProfileImage());
        response.setId(d.getId().toString());
        response.setMobile(d.getMobile());
        return response;
    }

    /** driverIds may arrive as a JSON array, a List (already deserialized), or a comma-separated string. */
    private List<UUID> parseDriverIds(Object driverIdsParam) {
        List<String> rawIds = new ArrayList<>();
        if (driverIdsParam instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    rawIds.add(String.valueOf(item));
                }
            }
        } else if (driverIdsParam instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.startsWith("[")) {
                try {
                    JsonNode parsed = objectMapper.readTree(trimmed);
                    if (parsed.isArray()) {
                        for (JsonNode node : parsed) {
                            rawIds.add(node.asText());
                        }
                    }
                } catch (Exception e) {
                    rawIds.addAll(List.of(trimmed.split(",")));
                }
            } else if (!trimmed.isEmpty()) {
                rawIds.addAll(List.of(trimmed.split(",")));
            }
        }

        List<UUID> ids = new ArrayList<>();
        for (String raw : rawIds) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed ids - legacy Mongo ObjectId() conversion would have thrown for the
                // whole request, but individual id filtering keeps this endpoint resilient.
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // kyc_verify
    // ------------------------------------------------------------------

    @Transactional
    public DriverResponse kycVerify(KycVerifyRequest request) {
        UUID driverId = requireUuid(request.get_id(), "Driver ID Required");

        DriverDocument document = driverDocumentRepository.findByDriver_IdAndDocKey(driverId, request.getIdtype())
                .orElse(null);
        if (document == null) {
            throw new ApiException(400, "KYC Update Failed");
        }
        document.setStatus(request.getVerifyStatus());
        if ("vehicle_images".equals(request.getIdtype())) {
            var fileStatus = objectMapper.createObjectNode();
            fileStatus.put("front", request.getVerifyStatus());
            fileStatus.put("back", request.getVerifyStatus());
            fileStatus.put("left", request.getVerifyStatus());
            fileStatus.put("right", request.getVerifyStatus());
            document.setFileStatus(fileStatus);
        }
        driverDocumentRepository.save(document);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(400, "KYC Update Failed"));
        List<DriverDocument> documents = driverDocumentRepository.findByDriver_Id(driverId);
        boolean hasPendingDocs = documents.stream()
                .anyMatch(doc -> doc.getStatus() != null && PENDING_DOC_STATUSES.contains(doc.getStatus()));

        driver.setKycCompleted(!hasPendingDocs);
        driver.setUnverifiedDriver(hasPendingDocs);
        driver.setKycStatus(hasPendingDocs ? "pending" : "approved");
        Driver saved = driverRepository.save(driver);

        // Legacy also fires a best-effort "kyc" mail notification here when hasPendingDocs is true
        // (sendMailNotification); no mail client exists yet in driver-service to reproduce that
        // side effect.

        return driverResponseMapper.toResponseWithDocsAndContacts(saved);
    }

    // ------------------------------------------------------------------
    // drivers_kyc_list
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> driversKycList(DriversKycListRequest request) {
        String searchText = request.getSearchText() == null ? "" : request.getSearchText().trim();
        String kycStatus = request.getKycStatus() == null || request.getKycStatus().isBlank()
                ? "pending" : request.getKycStatus();
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 10;

        if (!ALLOWED_KYC_STATUSES.contains(kycStatus)) {
            throw new ApiException(400, "Invalid kycStatus");
        }

        Specification<Driver> spec = Specification.where(DriverSpecifications.deletedEq(false))
                .and(DriverSpecifications.kycStatusEq(kycStatus));
        if ("approved".equals(kycStatus)) {
            spec = spec.and(DriverSpecifications.kycCompletedEq(true));
        }
        if (!searchText.isEmpty()) {
            spec = spec.and(DriverSpecifications.searchText(searchText));
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageResult = driverRepository.findAll(spec, pageable);

        Map<String, Object> result = new LinkedHashMap<>();
        if (pageResult.isEmpty()) {
            result.put("drivers", List.of());
            result.put("total", 0);
            return result;
        }

        result.put("drivers", pageResult.getContent().stream().map(this::toKycListRow).toList());
        result.put("total", pageResult.getTotalElements());
        return result;
    }

    private Map<String, Object> toKycListRow(Driver d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", d.getId());
        row.put("firstName", d.getFirstName());
        row.put("lastName", d.getLastName());
        row.put("mobile", d.getMobile());
        row.put("email", d.getEmail());
        row.put("city", d.getCity());
        row.put("state", d.getState());
        row.put("kycStatus", d.getKycStatus());
        row.put("unverifiedDriver", d.isUnverifiedDriver());
        row.put("vehicleNumber", d.getVehicleNumber());
        row.put("vehicleType", d.getVehicleType());
        row.put("vehicleVariant", d.getVehicleVariant());
        row.put("createdAt", d.getCreatedAt());
        return row;
    }

    // ------------------------------------------------------------------
    // getDrivers
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DriverResponse> getDrivers(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit > 0 ? limit : 10);
        List<Driver> drivers = driverRepository.findAll(pageable).getContent();
        if (drivers.isEmpty()) {
            throw new ApiException(404, "Drivers not found");
        }
        return drivers.stream().map(driverResponseMapper::toResponseExcludingDocsAndBank).toList();
    }

    // ------------------------------------------------------------------
    // update_ridehailDrivers
    // ------------------------------------------------------------------

    @Transactional
    public DriverResponse updateRideHailDrivers(UpdateRideHailDriverRequest request) {
        UUID driverId = requireUuid(request.get_id(), "Driver ID Required");
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(400, "Not Updated"));

        String type = request.getType();
        String action = request.getAction();

        if ("updatestatus".equals(type)) {
            driver.setStatus(action);
        } else if ("delete".equals(type)) {
            driver.setDeleted(true);
        } else if ("vehiclenumber".equals(action)) {
            driver.setVehicleNumber(request.getVehicleNumber());
        } else if ("vehicletype".equals(action)) {
            driver.setVehicleType(requireUuid(request.getVehicleType(), "Invalid vehicleType"));
        } else {
            throw new ApiException(400, "Invalid Update Action");
        }

        Driver saved = driverRepository.save(driver);
        return driverResponseMapper.toResponseWithDocsAndContacts(saved);
    }

    // ------------------------------------------------------------------
    // update_driver_trip
    // ------------------------------------------------------------------

    @Transactional
    public boolean updateDriverTrip(String driverIdRaw, String tripIdRaw, String status) {
        if (driverIdRaw == null || driverIdRaw.isBlank() || tripIdRaw == null || tripIdRaw.isBlank()
                || status == null || status.isBlank() || !DRIVER_TRIP_STATUSES.contains(status)) {
            return false;
        }

        UUID driverId;
        UUID tripId;
        try {
            driverId = UUID.fromString(driverIdRaw);
            tripId = UUID.fromString(tripIdRaw);
        } catch (IllegalArgumentException e) {
            return false;
        }

        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            return false;
        }

        switch (status) {
            case "rideaccept" -> {
                List<UUID> trips = new ArrayList<>(List.of(driver.getRecentTripIds()));
                trips.add(tripId);
                if (trips.size() > 5) {
                    trips = trips.subList(trips.size() - 5, trips.size());
                }
                driver.setRecentTripIds(trips.toArray(new UUID[0]));
                driver.setDriverStatus("rideaccept");
                driver.setOnRide(true);
            }
            case "pickup" -> driver.setDriverStatus("pickup");
            case "startride" -> driver.setDriverStatus("startride");
            case "endtrip" -> {
                driver.setDriverStatus("online");
                driver.setOnRide(false);
                driver.setLastTripAt(java.time.OffsetDateTime.now());
            }
            default -> {
                return false;
            }
        }

        driverRepository.save(driver);
        return true;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private UUID requireUuid(String value, String missingMessage) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, missingMessage);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, missingMessage);
        }
    }
}
