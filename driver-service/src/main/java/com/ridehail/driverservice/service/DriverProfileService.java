package com.ridehail.driverservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.driverservice.dto.DriverResponse;
import com.ridehail.driverservice.dto.UpdateBankDetailsForm;
import com.ridehail.driverservice.dto.UpdateDriverProfileForm;
import com.ridehail.driverservice.dto.UpdateLocationResponse;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverDocument;
import com.ridehail.driverservice.entity.DriverEmergencyContact;
import com.ridehail.driverservice.repository.DriverDocumentRepository;
import com.ridehail.driverservice.repository.DriverEmergencyContactRepository;
import com.ridehail.driverservice.repository.DriverRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Equivalent of driver.controller.js's update_profile / upload_documents / edit_kyc_documents /
 * get_profile / toggle_online_status / get_driver_location / update_location /
 * update_driver_fcm / update_bank_details / update_emergency_contact / updatePrefferedLocation.
 */
@Service
public class DriverProfileService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^(\\w+)(?:\\[(\\w+)\\])?$");

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverEmergencyContactRepository driverEmergencyContactRepository;
    private final DriverResponseMapper responseMapper;
    private final DriverImageUploadClient imageUploadClient;
    private final DriverActivityLogger activityLogger;
    private final InternalServiceClient internalServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String rideServiceUrl;

    public DriverProfileService(
            DriverRepository driverRepository,
            DriverDocumentRepository driverDocumentRepository,
            DriverEmergencyContactRepository driverEmergencyContactRepository,
            DriverResponseMapper responseMapper,
            DriverImageUploadClient imageUploadClient,
            DriverActivityLogger activityLogger,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.driverEmergencyContactRepository = driverEmergencyContactRepository;
        this.responseMapper = responseMapper;
        this.imageUploadClient = imageUploadClient;
        this.activityLogger = activityLogger;
        this.internalServiceClient = internalServiceClient;
        this.rideServiceUrl = rideServiceUrl;
    }

    @Transactional
    public DriverResponse updateProfile(HttpServletRequest request, String driverIdRaw, UpdateDriverProfileForm form) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        String imageUrl = driver.getProfileImage();
        if (form.getImageFiles() != null && !form.getImageFiles().isEmpty()) {
            List<String> uploaded = imageUploadClient.uploadToPublicDriver(List.of(form.getImageFiles()), List.of(""));
            if (!uploaded.isEmpty()) {
                imageUrl = uploaded.get(0);
            }
        }

        setIfPresent(form.getFirstName(), driver::setFirstName);
        setIfPresent(form.getLastName(), driver::setLastName);
        setIfPresent(form.getGender(), driver::setGender);
        setIfPresent(form.getVehicleNumber(), driver::setVehicleNumber);
        setIfPresent(form.getState(), driver::setState);
        setIfPresent(form.getCity(), driver::setCity);
        driver.setProfileImage(imageUrl == null ? "" : imageUrl);
        setIfPresent(form.getFcmToken(), driver::setFcmToken);
        setIfPresent(form.getChassisNumber(), driver::setChassisNumber);
        setIfPresent(form.getEngineNumber(), driver::setEngineNumber);
        setIfPresent(form.getFuelType(), driver::setFuelType);
        if (form.getOnRide() != null) {
            driver.setOnRide(form.getOnRide());
        }
        if (form.getProfileCompleted() != null) {
            driver.setProfileCompleted(form.getProfileCompleted());
        }
        setIfPresent(form.getEmail(), driver::setEmail);
        if (form.getVehicleType() != null && !form.getVehicleType().isBlank()) {
            driver.setVehicleType(parseUuid(form.getVehicleType(), "vehicleType"));
        }
        if (form.getVehicleVariant() != null && !form.getVehicleVariant().isBlank()) {
            driver.setVehicleVariant(parseUuid(form.getVehicleVariant(), "vehicleVariant"));
        }
        if (form.getMobile() != null && !form.getMobile().isBlank()) {
            driver.setMobile("+91" + form.getMobile());
        }
        if (form.getPreferredLocations() != null && !form.getPreferredLocations().isBlank()) {
            driver.setPreferredLocations(parseJson(form.getPreferredLocations()));
        }
        if (form.getDriverStatus() != null && !form.getDriverStatus().isBlank()) {
            driver.setDriverStatus(form.getDriverStatus());
        }
        // Mirrors the original's odd "isBlocked" handling: the body field actually toggles the
        // soft-delete flag, not the isBlocked column - see Driver's class notes / Foundation notes.
        if (form.getIsBlocked() != null) {
            driver.setDeleted(form.getIsBlocked());
        }

        driver = driverRepository.save(driver);
        activityLogger.log(request, driverId, "updateUser");
        return responseMapper.toResponseWithDocsAndContacts(driver);
    }

    @Transactional
    public DriverResponse uploadDocuments(HttpServletRequest request, String driverIdRaw, Map<String, MultipartFile> files) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        List<DriverDocument> driverDocs = driverDocumentRepository.findByDriver_Id(driverId);
        List<String> existingKeys = driverDocs.stream().map(DriverDocument::getDocKey).toList();

        Map<String, Map<String, MultipartFile>> grouped = groupByDocKey(files);
        if (grouped.isEmpty()) {
            throw new ApiException(400, "No files uploaded");
        }
        List<String> invalid = grouped.keySet().stream().filter(k -> !existingKeys.contains(k)).toList();
        if (!invalid.isEmpty()) {
            throw new ApiException(400, "Invalid document keys: " + String.join(", ", invalid));
        }

        Map<String, Map<String, String>> uploadResults = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, MultipartFile>> entry : grouped.entrySet()) {
            String docKey = entry.getKey();
            Map<String, String> subUrls = new LinkedHashMap<>();
            for (Map.Entry<String, MultipartFile> sub : entry.getValue().entrySet()) {
                String subFolder = driverId + "/" + docKey + "/" + sub.getKey();
                List<String> uploaded = imageUploadClient.uploadToPublicDriver(List.of(sub.getValue()), List.of(subFolder));
                if (!uploaded.isEmpty()) {
                    subUrls.put(sub.getKey(), uploaded.get(0));
                }
            }
            uploadResults.put(docKey, subUrls);
        }

        for (DriverDocument doc : driverDocs) {
            Map<String, String> newFiles = uploadResults.get(doc.getDocKey());
            if (newFiles == null) {
                continue;
            }
            ObjectNode mergedFiles = doc.getFiles() != null && doc.getFiles().isObject()
                    ? doc.getFiles().deepCopy() : objectMapper.createObjectNode();
            ObjectNode mergedStatus = doc.getFileStatus() != null && doc.getFileStatus().isObject()
                    ? doc.getFileStatus().deepCopy() : objectMapper.createObjectNode();
            for (Map.Entry<String, String> e : newFiles.entrySet()) {
                mergedFiles.put(e.getKey(), e.getValue());
                mergedStatus.put(e.getKey(), "pending");
            }
            doc.setFiles(mergedFiles);
            doc.setFileStatus(mergedStatus);

            if ("vehicle_images".equals(doc.getDocKey())) {
                boolean allPending = List.of("front", "back", "left", "right").stream()
                        .allMatch(key -> "pending".equals(mergedStatus.path(key).asText(null)));
                if (allPending) {
                    doc.setStatus("pending");
                }
            } else if (!newFiles.isEmpty()) {
                doc.setStatus("pending");
            }
            driverDocumentRepository.save(doc);
        }

        driver.setKycStatus("pending");
        driver = driverRepository.save(driver);

        ObjectNode meta = activityLogger.mapper().createObjectNode();
        meta.putPOJO("uploadedKeys", uploadResults.keySet());
        activityLogger.log(request, driverId, "documentUploaded", meta);

        return responseMapper.toResponseWithDocsAndContacts(driver);
    }

    @Transactional
    public DriverResponse editKycDocuments(HttpServletRequest request, String driverIdRaw, Map<String, List<MultipartFile>> files) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        List<DriverDocument> driverDocs = driverDocumentRepository.findByDriver_Id(driverId);
        List<String> existingKeys = driverDocs.stream().map(DriverDocument::getDocKey).toList();

        if (files.isEmpty()) {
            throw new ApiException(400, "No files uploaded");
        }
        List<String> invalid = files.keySet().stream().filter(k -> !existingKeys.contains(k)).toList();
        if (!invalid.isEmpty()) {
            throw new ApiException(400, "Invalid document keys: " + String.join(", ", invalid));
        }

        Map<String, List<String>> uploadResults = new LinkedHashMap<>();
        for (Map.Entry<String, List<MultipartFile>> entry : files.entrySet()) {
            String key = entry.getKey();
            List<String> subFolders = new ArrayList<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                subFolders.add(driverId + "/" + key);
            }
            List<String> uploaded = imageUploadClient.uploadToPublicDriver(entry.getValue(), subFolders);
            uploadResults.put(key, uploaded);
        }

        for (DriverDocument doc : driverDocs) {
            List<String> urls = uploadResults.get(doc.getDocKey());
            if (urls == null) {
                continue;
            }
            doc.setFiles(objectMapper.valueToTree(urls));
            doc.setStatus("pending");
            driverDocumentRepository.save(doc);
        }

        driver.setKycStatus("pending");
        driver = driverRepository.save(driver);

        ObjectNode meta = activityLogger.mapper().createObjectNode();
        meta.putPOJO("updatedKeys", uploadResults.keySet());
        activityLogger.log(request, driverId, "documentReuploaded", meta);

        return responseMapper.toResponseWithDocsAndContacts(driver);
    }

    public DriverResponse getProfile(String driverIdRaw, boolean isRideCheck) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(400, "driverId is required");
        }
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        DriverResponse response = responseMapper.toFullResponse(driver);

        if (isRideCheck) {
            JsonNode rideResp = internalServiceClient.get(rideServiceUrl + "/active/driver/" + driverId);
            if (rideResp != null && rideResp.path("status").asBoolean(false) && rideResp.hasNonNull("data")) {
                response.setCurrentRide(rideResp.path("data"));
            }
        }
        return response;
    }

    @Transactional
    public DriverResponse toggleOnlineStatus(HttpServletRequest request, String driverIdRaw, String driverStatus, JsonNode currentLocation) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        driver.setDriverStatus(driverStatus);
        applyGeoPoint(driver, currentLocation);
        driver.setLastOnlineAt(java.time.OffsetDateTime.now());
        driver = driverRepository.save(driver);

        activityLogger.log(request, driverId, "toggleOnline");
        return responseMapper.toResponseWithDocsAndContacts(driver);
    }

    public UpdateLocationResponse getDriverLocation(String driverIdRaw) {
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));
        return new UpdateLocationResponse(driver.getCurrentLatitude(), driver.getCurrentLongitude());
    }

    @Transactional
    public UpdateLocationResponse updateLocation(HttpServletRequest request, String driverIdRaw, Double lat, Double lng) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        driver.setCurrentLatitude(lat);
        driver.setCurrentLongitude(lng);
        driverRepository.save(driver);

        activityLogger.log(request, driverId, "updateLocation");
        return new UpdateLocationResponse(lat, lng);
    }

    @Transactional
    public String updateDriverFcm(HttpServletRequest request, String driverIdRaw, String fcmToken) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(400, "driverId is required");
        }
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        driver.setFcmToken(fcmToken);
        driver = driverRepository.save(driver);

        activityLogger.log(request, driverId, "updateFcm");
        return driver.getFcmToken();
    }

    @Transactional
    public DriverResponse.BankDetailsResponse updateBankDetails(HttpServletRequest request, String driverIdRaw, UpdateBankDetailsForm form) {
        UUID driverId = requireDriverId(driverIdRaw);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        MultipartFile qrFile = form.getUpiQrImage() != null && !form.getUpiQrImage().isEmpty() ? form.getUpiQrImage() : form.getUpiQr();
        if (qrFile != null && !qrFile.isEmpty()) {
            List<String> uploaded = imageUploadClient.uploadToPublicDriver(List.of(qrFile), List.of(""));
            if (!uploaded.isEmpty()) {
                driver.setBankUpiQrImage(uploaded.get(0));
            }
        }

        setIfPresent(form.getHolderName(), driver::setBankHolderName);
        setIfPresent(form.getAccountNumber(), driver::setBankAccountNumber);
        setIfPresent(form.getIfsc(), driver::setBankIfsc);
        setIfPresent(form.getBankName(), driver::setBankName);
        setIfPresent(form.getBranch(), driver::setBankBranch);
        setIfPresent(form.getUpiId(), driver::setBankUpiId);

        driver = driverRepository.save(driver);

        DriverResponse.BankDetailsResponse bankDetails = new DriverResponse.BankDetailsResponse();
        bankDetails.setAccountNumber(driver.getBankAccountNumber());
        bankDetails.setIfsc(driver.getBankIfsc());
        bankDetails.setHolderName(driver.getBankHolderName());
        bankDetails.setBankName(driver.getBankName());
        bankDetails.setBranch(driver.getBankBranch());
        bankDetails.setUpiId(driver.getBankUpiId());
        bankDetails.setUpiQrImage(driver.getBankUpiQrImage());

        ObjectNode meta = activityLogger.mapper().createObjectNode();
        meta.putPOJO("bankDetails", bankDetails);
        activityLogger.log(request, driverId, "bankDetailsUpdated", meta);

        return bankDetails;
    }

    @Transactional
    public List<DriverResponse.EmergencyContactResponse> updateEmergencyContact(
            HttpServletRequest request, String driverIdRaw, String name, String relation, String phone, String contactId) {
        UUID driverId = requireDriverId(driverIdRaw);
        driverRepository.findById(driverId).orElseThrow(() -> new ApiException(404, "Driver not found"));

        List<DriverEmergencyContact> contacts = driverEmergencyContactRepository.findByDriver_Id(driverId);

        if (contactId != null && !contactId.isBlank()) {
            UUID id = parseUuid(contactId, "contactId");
            DriverEmergencyContact toUpdate = contacts.stream().filter(c -> c.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new ApiException(400, "Invalid emergency contact ID"));
            toUpdate.setName(name);
            toUpdate.setRelation(relation);
            toUpdate.setPhone(phone);
            driverEmergencyContactRepository.save(toUpdate);
        } else {
            if (contacts.size() >= 2) {
                throw new ApiException(400, "You can only add a maximum of 2 emergency contacts");
            }
            Driver driverRef = driverRepository.getReferenceById(driverId);
            DriverEmergencyContact created = DriverEmergencyContact.builder()
                    .driver(driverRef)
                    .name(name)
                    .relation(relation == null ? "" : relation)
                    .phone(phone)
                    .build();
            driverEmergencyContactRepository.save(created);
        }

        List<DriverEmergencyContact> updated = driverEmergencyContactRepository.findByDriver_Id(driverId);
        List<DriverResponse.EmergencyContactResponse> response = new ArrayList<>();
        for (DriverEmergencyContact c : updated) {
            DriverResponse.EmergencyContactResponse dto = new DriverResponse.EmergencyContactResponse();
            dto.setId(c.getId().toString());
            dto.setName(c.getName());
            dto.setRelation(c.getRelation());
            dto.setPhone(c.getPhone());
            response.add(dto);
        }

        ObjectNode meta = activityLogger.mapper().createObjectNode();
        meta.putPOJO("emergencyContacts", response);
        activityLogger.log(request, driverId, "emergencyContactUpdate", meta);

        return response;
    }

    @Transactional
    public JsonNode updatePreferredLocation(String driverIdRaw, JsonNode preferredLocations) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(404, "Driver not found");
        }
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(400, "Driver preferred location not updated"));

        if (preferredLocations != null) {
            driver.setPreferredLocations(preferredLocations);
        }
        driver = driverRepository.save(driver);
        return driver.getPreferredLocations();
    }

    private void applyGeoPoint(Driver driver, JsonNode point) {
        if (point == null || point.isMissingNode() || point.isNull()) {
            return;
        }
        JsonNode coordinates = point.path("coordinates");
        if (coordinates.isArray() && coordinates.size() == 2) {
            driver.setCurrentLongitude(coordinates.get(0).asDouble());
            driver.setCurrentLatitude(coordinates.get(1).asDouble());
        }
    }

    private Map<String, Map<String, MultipartFile>> groupByDocKey(Map<String, MultipartFile> files) {
        Map<String, Map<String, MultipartFile>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            Matcher matcher = FIELD_KEY_PATTERN.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            String docKey = matcher.group(1);
            String subKey = matcher.group(2) != null ? matcher.group(2) : "file";
            grouped.computeIfAbsent(docKey, k -> new LinkedHashMap<>()).put(subKey, entry.getValue());
        }
        return grouped;
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new ApiException(400, "Invalid preferredLocations");
        }
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private UUID requireDriverId(String driverIdRaw) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(400, "driverId is required");
        }
        return parseUuid(driverIdRaw, "driverId");
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid " + fieldName);
        }
    }
}
