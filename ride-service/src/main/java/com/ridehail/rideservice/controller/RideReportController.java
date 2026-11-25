package com.ridehail.rideservice.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.JwtService;
import com.ridehail.rideservice.config.InvoiceAuthBypassConfig;
import com.ridehail.rideservice.service.RideReportService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Ports ride.controller.js: export_report (GET /ride-apis/export) and generate_ride_invoice
 * (GET /ride-apis/rides/:rideId/invoice). Both stream a file and bypass the JSON/encryption
 * envelope, so they return a raw {@link ResponseEntity} rather than going through ApiResponseService
 * (matching the legacy {@code res.setHeader(...); res.send(buffer)} behaviour).
 */
@RestController
@RequestMapping("/ride-apis")
public class RideReportController {

    private final RideReportService service;
    private final JwtService jwtService;
    private final String driverSecret;
    private final String inAppSecret;

    public RideReportController(RideReportService service, JwtService jwtService,
            @Value("${ridehail.jwt.driver-secret}") String driverSecret,
            @Value("${ridehail.jwt.in-app-secret}") String inAppSecret) {
        this.service = service;
        this.jwtService = jwtService;
        this.driverSecret = driverSecret;
        this.inAppSecret = inAppSecret;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "rideType", required = false) String rideType) {
        RideReportService.Export export = service.exportReport(type, search, fromDate, toDate, rideType);
        return file(export, true);
    }

    @GetMapping("/rides/{rideId}/invoice")
    public ResponseEntity<byte[]> generateRideInvoice(
            @PathVariable String rideId,
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) {

        boolean browserAuth = Boolean.TRUE.equals(request.getAttribute(InvoiceAuthBypassConfig.BROWSER_AUTH_ATTRIBUTE));
        String internalHeader = request.getHeader("x-internal-secret");
        boolean isInternal = !browserAuth && internalHeader != null && internalHeader.equals(inAppSecret);

        if (!isInternal) {
            String jwt = token;
            if (jwt == null) {
                String authHeader = request.getHeader("authorization");
                if (authHeader != null && authHeader.contains(" ")) {
                    jwt = authHeader.substring(authHeader.indexOf(' ') + 1);
                }
            }
            if (jwt == null || jwt.isBlank()) {
                throw new ApiException(StatusCode.UNAUTHORIZED, "Unauthorised");
            }
            Map<String, Object> verified = jwtService.verify(jwt, driverSecret);
            if (verified == null) {
                throw new ApiException(StatusCode.UNAUTHORIZED, "Invalid token");
            }
        }

        RideReportService.Export export = service.generateInvoice(rideId);
        return file(export, isInternal);
    }

    private ResponseEntity<byte[]> file(RideReportService.Export export, boolean attachment) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(export.contentType()));
        headers.setContentDisposition(ContentDisposition
                .builder(attachment ? "attachment" : "inline")
                .filename(export.filename())
                .build());
        return new ResponseEntity<>(export.body(), headers, org.springframework.http.HttpStatus.OK);
    }
}
