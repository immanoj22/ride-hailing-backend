package com.ridehail.rideservice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.dto.AddPriceRequest;
import com.ridehail.rideservice.dto.AddVehicleTypeRequest;
import com.ridehail.rideservice.dto.AddVehicleVariantRequest;
import com.ridehail.rideservice.dto.UpdateVehicleTypeRequest;
import com.ridehail.rideservice.dto.UpdateVehicleVariantRequest;
import com.ridehail.rideservice.service.PriceService;
import com.ridehail.rideservice.service.VehicleService;

import jakarta.validation.Valid;

/**
 * Port of routes/vehicle.routes.js (mounted at /vehicle-apis, server.js line 90) - vehicle
 * type/variant CRUD, price catalog CRUD, vehicle lookups and exports.
 */
@RestController
@RequestMapping("/vehicle-apis")
public class VehicleController {

    private final VehicleService vehicleService;
    private final PriceService priceService;
    private final ApiResponseService apiResponseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VehicleController(VehicleService vehicleService, PriceService priceService,
                             ApiResponseService apiResponseService) {
        this.vehicleService = vehicleService;
        this.priceService = priceService;
        this.apiResponseService = apiResponseService;
    }

    /* ------------------------------------------------------------- vehicle type */

    @PostMapping(value = "/add-vehicle-type", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addVehicleType(@Valid @ModelAttribute AddVehicleTypeRequest req,
                                            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return apiResponseService.send(true, "success", 201, "Vehicle type created successfully",
                vehicleService.addVehicleType(req, imageFile));
    }

    @GetMapping("/vehicle-type-list")
    public ResponseEntity<?> vehicleTypeList(@RequestParam(required = false) String page,
                                             @RequestParam(required = false) String limit,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String vehicleTypeId) {
        return apiResponseService.ok("Vehicle types fetched successfully",
                vehicleService.vehicleTypeList(page, limit, search, vehicleTypeId));
    }

    @PostMapping(value = "/update-vehicle-type", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateVehicleType(@Valid @ModelAttribute UpdateVehicleTypeRequest req,
                                               @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return apiResponseService.ok("Vehicle type updated successfully",
                vehicleService.updateVehicleType(req, imageFile));
    }

    /* ---------------------------------------------------------- vehicle variant */

    @PostMapping(value = "/add-vehicle-variant", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addVehicleVariant(@Valid @ModelAttribute AddVehicleVariantRequest req,
                                               @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return apiResponseService.send(true, "success", 201, "Vehicle variant created successfully",
                vehicleService.addVehicleVariant(req, imageFile));
    }

    @GetMapping("/vehicle-variant-list")
    public ResponseEntity<?> vehicleVariantList(@RequestParam(required = false) String page,
                                                @RequestParam(required = false) String limit,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(required = false) String vehicleTypeId,
                                                @RequestParam(required = false) String isLocal,
                                                @RequestParam(required = false) String isOutstation) {
        return apiResponseService.ok("Vehicle variants fetched successfully",
                vehicleService.vehicleVariantList(page, limit, search, vehicleTypeId, isLocal, isOutstation));
    }

    @PostMapping(value = "/update-vehicle-variant", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateVehicleVariant(@Valid @ModelAttribute UpdateVehicleVariantRequest req,
                                                  @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return apiResponseService.ok("Vehicle variant updated successfully",
                vehicleService.updateVehicleVariant(req, imageFile));
    }

    /* ----------------------------------------------------------- price catalog */

    @PostMapping("/add-price")
    public ResponseEntity<?> addPrice(@Valid @RequestBody AddPriceRequest req) {
        return apiResponseService.send(true, "success", 201, "Price created successfully",
                priceService.addPrice(req));
    }

    @GetMapping("/price-list")
    public ResponseEntity<?> priceList(@RequestParam(required = false) String page,
                                       @RequestParam(required = false) String limit,
                                       @RequestParam(required = false) String vehicleTypeId,
                                       @RequestParam(required = false) String vehicleVariantId,
                                       @RequestParam(required = false) String brand,
                                       @RequestParam(name = "delete", required = false) String delFlag) {
        return apiResponseService.ok("Prices fetched successfully",
                priceService.priceList(page, limit, vehicleTypeId, vehicleVariantId, brand, delFlag));
    }

    @PostMapping("/update-price")
    public ResponseEntity<?> updatePrice(@RequestBody JsonNode body) {
        return apiResponseService.ok("Price updated successfully", priceService.updatePrice(body));
    }

    /* --------------------------------------------------------------- lookups */

    @GetMapping("/vehicle-all-vehicles-with-km")
    public ResponseEntity<?> getAllVehiclesWithKm(@RequestParam(required = false) String isLocal,
                                                  @RequestParam(required = false) String isOutstation,
                                                  @RequestParam(required = false) String km,
                                                  @RequestParam(required = false) String time,
                                                  @RequestParam(required = false) String bookingDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String tripType,
                                                  @RequestParam(required = false) String toll,
                                                  @RequestParam(required = false) String promo) {
        return apiResponseService.ok("Vehicles fetched successfully",
                vehicleService.getAllVehiclesWithKm(isLocal, isOutstation, km, time, bookingDate, endDate,
                        tripType, toll, parseJson(promo)));
    }

    @GetMapping("/get-outstation-price-for-vehicle")
    public ResponseEntity<?> getOutstationPriceForVehicle(@RequestParam(required = false) String id,
                                                          @RequestParam(required = false) String tripType,
                                                          @RequestParam(required = false) String km,
                                                          @RequestParam(required = false) String bookingDate,
                                                          @RequestParam(required = false) String endDate,
                                                          @RequestParam(required = false) String packageId,
                                                          @RequestParam(required = false) String promo) {
        return apiResponseService.ok("Price details fetched successfully",
                vehicleService.getOutstationPriceForVehicle(id, tripType, km, bookingDate, endDate, packageId,
                        parseJson(promo)));
    }

    @GetMapping("/get-rental-vehicle")
    public ResponseEntity<?> getRentalVehicles() {
        return apiResponseService.ok("Rental vehicles fetched successfully", vehicleService.getRentalVehicles());
    }

    /* --------------------------------------------------------------- exports */

    @GetMapping("/export")
    public ResponseEntity<?> vehicleTypeExport(@RequestParam(name = "type", required = false) String fileType,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(required = false) String vehicleTypeId) {
        return vehicleService.vehicleTypeExport(fileType, search, vehicleTypeId);
    }

    @GetMapping("/variant-export")
    public ResponseEntity<?> vehicleVariantExport(@RequestParam(name = "type", required = false) String fileType,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(required = false) String vehicleTypeId,
                                                  @RequestParam(required = false) String tripTypeId) {
        return vehicleService.vehicleVariantExport(fileType, search, vehicleTypeId, tripTypeId);
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
