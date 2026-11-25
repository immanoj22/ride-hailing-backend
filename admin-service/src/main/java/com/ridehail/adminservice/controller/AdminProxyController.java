package com.ridehail.adminservice.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.adminservice.service.AdminProxyService;

/**
 * Port of the "reports / fleet / tracking / cross-service proxy" functions of
 * Admin-service/controller/admin.controller.js. Routes mount under /admin-apis
 * (Admin-service/server.js: {@code app.use("/admin-apis", routes)}); every path/method matches
 * Admin-service/routes/admin.routes.js exactly. All logic is in {@link AdminProxyService}.
 */
@RestController
@RequestMapping("/admin-apis")
public class AdminProxyController {

    private final AdminProxyService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdminProxyController(AdminProxyService service) {
        this.service = service;
    }

    private JsonNode body(JsonNode b) {
        return b == null ? mapper.createObjectNode() : b;
    }

    // ---------------------------------------------------------------- sos / support / chat

    @PostMapping("/sos")
    public ResponseEntity<?> getSos(@RequestBody(required = false) JsonNode body) {
        return service.getSos(body(body));
    }

    @GetMapping("/support")
    public ResponseEntity<?> getSupport(@RequestParam Map<String, String> query) {
        return service.getSupport(query);
    }

    @DeleteMapping("/close-support-ticket")
    public ResponseEntity<?> closeSupport(@RequestParam(required = false) String ticketId,
            @RequestParam(required = false) String closingMessage) {
        return service.closeSupport(ticketId, closingMessage);
    }

    @PatchMapping("/reopen-support-ticket")
    public ResponseEntity<?> reopenSupport(@RequestParam(required = false) String ticketId) {
        return service.reopenSupport(ticketId);
    }

    @PostMapping(value = "/addchat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addChat(
            @RequestParam(value = "msg", required = false) String msg,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "ticketId", required = false) String ticketId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.addChat(msg, from, userId, ticketId, file);
    }

    @GetMapping("/getchats")
    public ResponseEntity<?> getChats(@RequestParam(required = false) String ticketId) {
        return service.getChats(ticketId);
    }

    // ---------------------------------------------------------------- reports

    @PostMapping("/reports/ride")
    public ResponseEntity<?> getRideSummary(@RequestBody(required = false) JsonNode body) {
        return service.getRideSummary(body(body));
    }

    @GetMapping("/reports/ride-activity")
    public ResponseEntity<?> getRideActivityReport(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return service.getRideActivityReport(from, to);
    }

    @GetMapping("/get-all-rides")
    public ResponseEntity<?> getAllRides(@RequestParam Map<String, String> query) {
        return service.getAllRides(query);
    }

    @GetMapping("/reports/user")
    public ResponseEntity<?> getUsersReport() {
        return service.getUsersReport();
    }

    @PostMapping("/reports/user-list")
    public ResponseEntity<?> getUsersReportList(@RequestBody(required = false) JsonNode body) {
        return service.getUsersReportList(body(body));
    }

    @GetMapping("/reports/user-ride/{userId}")
    public ResponseEntity<?> getUserRideReport(@PathVariable String userId,
            @RequestParam Map<String, String> query) {
        return service.getUserRideReport(userId, query);
    }

    @PostMapping("/reports/update-user")
    public ResponseEntity<?> updateUser(@RequestBody(required = false) JsonNode body) {
        return service.updateUser(body(body));
    }

    @GetMapping("/reports/driver")
    public ResponseEntity<?> getDriversReport() {
        return service.getDriversReport();
    }

    @PostMapping("/reports/update-driver")
    public ResponseEntity<?> updateDriver(@RequestBody(required = false) JsonNode body) {
        return service.updateDriver(body(body));
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<?> getDriverDetails(@PathVariable String driverId) {
        return service.getDriverDetails(driverId);
    }

    @PostMapping("/reports/driver-list")
    public ResponseEntity<?> getDriversReportList(@RequestBody(required = false) JsonNode body) {
        return service.getDriversReportList(body(body));
    }

    @PostMapping("/reports/driver-ride/{driverId}")
    public ResponseEntity<?> getDriversRideReport(@PathVariable String driverId,
            @RequestBody(required = false) JsonNode body) {
        return service.getDriversRideReport(driverId, body(body));
    }

    // ---------------------------------------------------------------- driver wallet claims

    @PostMapping("/driver-wallet-claims")
    public ResponseEntity<?> getDriverWalletClaimRequests(@RequestBody(required = false) JsonNode body) {
        return service.getDriverWalletClaimRequests(body(body));
    }

    @PostMapping("/driver-wallet-claim-review")
    public ResponseEntity<?> reviewDriverWalletClaimRequest(@RequestBody(required = false) JsonNode body) {
        return service.reviewDriverWalletClaimRequest(body(body));
    }

    @PostMapping("/driver-wallet-claim-pay")
    public ResponseEntity<?> payDriverWalletClaimRequest(@RequestBody(required = false) JsonNode body) {
        return service.payDriverWalletClaimRequest(body(body));
    }

    // ---------------------------------------------------------------- kyc

    @PostMapping("/drivers/kyc")
    public ResponseEntity<?> getDriversKycList(@RequestBody(required = false) JsonNode body) {
        return service.getDriversKycList(body(body));
    }

    @PostMapping("/drivers-documents/{driverId}/{key}")
    public ResponseEntity<?> verifyDriverDocument(@PathVariable String driverId, @PathVariable String key,
            @RequestBody(required = false) JsonNode body) {
        return service.verifyDriverDocument(driverId, key, body(body));
    }

    // ---------------------------------------------------------------- vehicle type / variant / price

    @GetMapping("/vehicle-variant-list")
    public ResponseEntity<?> vehicleVariantList(@RequestParam Map<String, String> query) {
        return service.vehicleVariantList(query);
    }

    @GetMapping("/vehicle-type-list")
    public ResponseEntity<?> vehicleTypeList(@RequestParam Map<String, String> query) {
        return service.vehicleTypeList(query);
    }

    @PostMapping("/add-vehicle-variant")
    public ResponseEntity<?> addVehicleVariant(@RequestParam Map<String, String> fields,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        return service.addVehicleVariant(fields, imageFile);
    }

    @PostMapping("/update-vehicle-variant")
    public ResponseEntity<?> updateVehicleVariant(@RequestParam Map<String, String> fields,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        return service.updateVehicleVariant(fields, imageFile);
    }

    @PostMapping("/add-vehicle-type")
    public ResponseEntity<?> addVehicleType(@RequestParam Map<String, String> fields,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        return service.addVehicleType(fields, imageFile);
    }

    @PostMapping("/update-vehicle-type")
    public ResponseEntity<?> updateVehicleType(@RequestParam Map<String, String> fields,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        return service.updateVehicleType(fields, imageFile);
    }

    @PostMapping("/add-price")
    public ResponseEntity<?> addPrice(@RequestBody(required = false) JsonNode body) {
        return service.addPrice(body(body));
    }

    @PostMapping("/update-price")
    public ResponseEntity<?> updatePrice(@RequestBody(required = false) JsonNode body) {
        return service.updatePrice(body(body));
    }

    // ---------------------------------------------------------------- users list / rides / tracking

    @GetMapping("/get-users-list")
    public ResponseEntity<?> getUsersList(@RequestParam Map<String, String> query) {
        return service.getUsersList(query);
    }

    @PostMapping("/get-outstation-ride")
    public ResponseEntity<?> getOutstationRide(@RequestBody(required = false) JsonNode body) {
        return service.getOutstationRide(body(body));
    }

    @GetMapping("/track/ongoing-rides")
    public ResponseEntity<?> getOngoingRidesAdmin(@RequestParam Map<String, String> query) {
        return service.getOngoingRidesAdmin(query);
    }

    @GetMapping("/track/ongoing-rides/{rideId}/gps")
    public ResponseEntity<?> getOngoingRideGps(@PathVariable String rideId,
            @RequestParam(required = false) String limit) {
        return service.getOngoingRideGps(rideId, limit);
    }

    @GetMapping("/get-driver-fetch-config")
    public ResponseEntity<?> getDriverFetchConfig() {
        return service.getDriverFetchConfig();
    }

    @PostMapping("/update-driver-fetch-config")
    public ResponseEntity<?> updateDriverFetchConfig(@RequestBody(required = false) JsonNode body) {
        return service.updateDriverFetchConfig(body(body));
    }

    // ---------------------------------------------------------------- export (binary passthrough)

    @GetMapping("/export")
    public ResponseEntity<?> exportReport(@RequestParam Map<String, String> query) {
        AdminProxyService.ExportPayload payload = service.export(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, payload.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + payload.filename())
                .body(payload.body());
    }
}
