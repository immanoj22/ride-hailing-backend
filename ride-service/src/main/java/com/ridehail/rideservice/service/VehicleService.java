package com.ridehail.rideservice.service;

import static com.ridehail.rideservice.service.VehicleCatalogSupport.priceMap;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.requireAdmin;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.toUuid;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.vehicleTypeMap;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.vehicleVariantMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.dto.AddVehicleTypeRequest;
import com.ridehail.rideservice.dto.AddVehicleVariantRequest;
import com.ridehail.rideservice.dto.UpdateVehicleTypeRequest;
import com.ridehail.rideservice.dto.UpdateVehicleVariantRequest;
import com.ridehail.rideservice.entity.Price;
import com.ridehail.rideservice.entity.PriceOnewayPackage;
import com.ridehail.rideservice.entity.PriceRentalPackage;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.repository.PriceOnewayPackageRepository;
import com.ridehail.rideservice.repository.PriceRentalPackageRepository;
import com.ridehail.rideservice.repository.PriceRepository;
import com.ridehail.rideservice.repository.VehicleTypeRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;
import com.ridehail.rideservice.util.OutstationFareCalculator;
import com.ridehail.rideservice.util.S3ImageUploader;
import com.ridehail.rideservice.util.TabularExportWriter;

/** Port of the vehicle type / variant / lookup / export functions of controller/vehicle.controller.js. */
@Service
public class VehicleService {

    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final PriceRepository priceRepository;
    private final PriceOnewayPackageRepository onewayPackageRepository;
    private final PriceRentalPackageRepository rentalPackageRepository;
    private final S3ImageUploader imageUploader;

    public VehicleService(VehicleTypeRepository vehicleTypeRepository,
                          VehicleVariantRepository vehicleVariantRepository,
                          PriceRepository priceRepository,
                          PriceOnewayPackageRepository onewayPackageRepository,
                          PriceRentalPackageRepository rentalPackageRepository,
                          S3ImageUploader imageUploader) {
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.priceRepository = priceRepository;
        this.onewayPackageRepository = onewayPackageRepository;
        this.rentalPackageRepository = rentalPackageRepository;
        this.imageUploader = imageUploader;
    }

    /* ------------------------------------------------------------------ vehicle type */

    @Transactional
    public Map<String, Object> addVehicleType(AddVehicleTypeRequest req, MultipartFile image) {
        requireAdmin();
        String type = req.getType() == null ? "" : req.getType().toLowerCase();
        if (vehicleTypeRepository.findByTypeIgnoreCaseAndDeletedFalse(type).isPresent()) {
            throw new ApiException(400, "Vehicle type already exist");
        }
        if (image == null || image.isEmpty()) {
            throw new ApiException(400, "Image is required");
        }
        String imageUrl = imageUploader.upload(image, "public_vehicle_type");
        VehicleType saved = vehicleTypeRepository.save(VehicleType.builder()
                .type(type)
                .label(req.getLabel() == null ? "" : req.getLabel())
                .description(req.getDescription() == null ? "" : req.getDescription())
                .image(imageUrl)
                .build());
        return vehicleTypeMap(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> vehicleTypeList(String page, String limit, String search, String vehicleTypeId) {
        String s = search == null ? "" : search.trim();
        Specification<VehicleType> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (!s.isEmpty()) {
            String like = "%" + s.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("type")), like),
                    cb.like(cb.lower(root.get("label")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }
        UUID vtId = toUuid(vehicleTypeId);
        if (vtId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), vtId));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<VehicleType> found;
        if (page != null && !page.isBlank() && limit != null && !limit.isBlank()) {
            int p = Integer.parseInt(page);
            int l = Integer.parseInt(limit);
            found = vehicleTypeRepository.findAll(spec, PageRequest.of(Math.max(p - 1, 0), l, sort)).getContent();
        } else {
            found = vehicleTypeRepository.findAll(spec, sort);
        }
        List<Map<String, Object>> result = found.stream().map(VehicleCatalogSupport::vehicleTypeMap).toList();
        long total = vehicleTypeRepository.count(spec);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("result", result);
        resp.put("total", total);
        return resp;
    }

    @Transactional
    public Map<String, Object> updateVehicleType(UpdateVehicleTypeRequest req, MultipartFile image) {
        requireAdmin();
        UUID id = toUuid(req.get_id());
        if (id == null) {
            throw new ApiException(400, "Invalid _id");
        }
        VehicleType vt = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Vehicle type not found or update failed"));
        boolean changed = false;
        if (image != null && !image.isEmpty()) {
            vt.setImage(imageUploader.upload(image, "public_vehicle_type"));
            changed = true;
        }
        if (req.getType() != null && !req.getType().isBlank()) {
            vt.setType(req.getType());
            changed = true;
        }
        if (req.getLabel() != null && !req.getLabel().isBlank()) {
            vt.setLabel(req.getLabel());
            changed = true;
        }
        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            vt.setDescription(req.getDescription());
            changed = true;
        }
        if (Boolean.TRUE.equals(req.getDelete())) {
            vt.setDeleted(true);
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Nothing to update");
        }
        return vehicleTypeMap(vehicleTypeRepository.save(vt));
    }

    /* --------------------------------------------------------------- vehicle variant */

    @Transactional
    public Map<String, Object> addVehicleVariant(AddVehicleVariantRequest req, MultipartFile image) {
        requireAdmin();
        if (image == null || image.isEmpty()) {
            throw new ApiException(400, "Image is required");
        }
        UUID vtId = toUuid(req.getVehicleTypeId());
        if (vtId == null || vehicleTypeRepository.findByIdAndDeletedFalse(vtId).isEmpty()) {
            throw new ApiException(400, "Invalid vehicleTypeId");
        }
        String imageUrl = imageUploader.upload(image, "public_vehicle_variant");
        VehicleVariant saved = vehicleVariantRepository.save(VehicleVariant.builder()
                .vehicleTypeId(vtId)
                .type(req.getType() == null ? "" : req.getType().toLowerCase())
                .label(req.getLabel() == null ? "" : req.getLabel())
                .description(req.getDescription() == null ? "" : req.getDescription())
                .image(imageUrl)
                .local(Boolean.TRUE.equals(req.getIsLocal()))
                .outstation(Boolean.TRUE.equals(req.getIsOutstation()))
                .seats(req.getSeats() == null ? 2 : req.getSeats())
                .build());
        return vehicleVariantMap(saved, null, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> vehicleVariantList(String page, String limit, String search,
                                                  String vehicleTypeId, String isLocal, String isOutstation) {
        String s = search == null ? "" : search.trim();
        Specification<VehicleVariant> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (!s.isEmpty()) {
            String like = "%" + s.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("type")), like),
                    cb.like(cb.lower(root.get("label")), like)));
        }
        UUID vtId = toUuid(vehicleTypeId);
        if (vtId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicleTypeId"), vtId));
        }
        if (isLocal != null) {
            boolean v = "true".equalsIgnoreCase(isLocal);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("local"), v));
        }
        if (isOutstation != null) {
            boolean v = "true".equalsIgnoreCase(isOutstation);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("outstation"), v));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<VehicleVariant> found;
        if (page != null && !page.isBlank() && limit != null && !limit.isBlank()) {
            int p = Integer.parseInt(page);
            int l = Integer.parseInt(limit);
            found = vehicleVariantRepository.findAll(spec, PageRequest.of(Math.max(p - 1, 0), l, sort)).getContent();
        } else {
            found = vehicleVariantRepository.findAll(spec, sort);
        }
        long total = vehicleVariantRepository.count(spec);
        List<Map<String, Object>> result = new ArrayList<>();
        for (VehicleVariant v : found) {
            Map<String, Object> vtMap = v.getVehicleTypeId() == null ? null
                    : vehicleTypeRepository.findById(v.getVehicleTypeId()).map(VehicleCatalogSupport::vehicleTypeMap).orElse(null);
            Map<String, Object> lp = v.getLocalPriceId() == null ? null
                    : priceRepository.findById(v.getLocalPriceId()).map(this::priceWithPackages).orElse(null);
            Map<String, Object> op = v.getOutstationPriceId() == null ? null
                    : priceRepository.findById(v.getOutstationPriceId()).map(this::priceWithPackages).orElse(null);
            result.add(vehicleVariantMap(v, vtMap, lp, op));
        }
        if (result.isEmpty()) {
            throw new ApiException(404, "Vehicle variants not found");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("result", result);
        resp.put("total", total);
        return resp;
    }

    @Transactional
    public Map<String, Object> updateVehicleVariant(UpdateVehicleVariantRequest req, MultipartFile image) {
        requireAdmin();
        UUID id = toUuid(req.get_id());
        if (id == null) {
            throw new ApiException(400, "Invalid _id");
        }
        VehicleVariant v = vehicleVariantRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Vehicle variant not found or update failed"));
        boolean changed = false;
        if (req.getVehicleTypeId() != null && !req.getVehicleTypeId().isBlank()) {
            UUID vtId = toUuid(req.getVehicleTypeId());
            if (vtId == null || vehicleTypeRepository.findByIdAndDeletedFalse(vtId).isEmpty()) {
                throw new ApiException(400, "Invalid vehicleTypeId");
            }
            v.setVehicleTypeId(vtId);
            changed = true;
        }
        if (image != null && !image.isEmpty()) {
            v.setImage(imageUploader.upload(image, "public_vehicle_variant"));
            changed = true;
        }
        if (req.getType() != null && !req.getType().isBlank()) {
            v.setType(req.getType().toLowerCase());
            changed = true;
        }
        if (req.getLabel() != null && !req.getLabel().isBlank()) {
            v.setLabel(req.getLabel());
            changed = true;
        }
        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            v.setDescription(req.getDescription());
            changed = true;
        }
        if (req.getIsLocal() != null) {
            v.setLocal(req.getIsLocal());
            changed = true;
        }
        if (req.getIsOutstation() != null) {
            v.setOutstation(req.getIsOutstation());
            changed = true;
        }
        if (req.getSeats() != null) {
            v.setSeats(req.getSeats());
            changed = true;
        }
        if (req.getDelete() != null) {
            v.setDeleted(req.getDelete());
            changed = true;
        }
        if (!changed) {
            throw new ApiException(400, "Nothing to update");
        }
        return vehicleVariantMap(vehicleVariantRepository.save(v), null, null, null);
    }

    /* -------------------------------------------------------------------- lookups */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllVehiclesWithKm(String isLocal, String isOutstation, String km, String time,
                                                          String bookingDate, String endDate, String tripType,
                                                          String toll, JsonNode promo) {
        boolean local = "true".equals(isLocal);
        boolean outstation = "true".equals(isOutstation);
        double days = daysBetween(bookingDate, endDate);
        int timeMin = time == null ? 0 : Math.floorDiv(parseInt(time), 60);
        double kmVal = km == null ? 0 : parseDouble(km);
        double tollVal = toll == null ? 0 : parseDouble(toll);

        List<VehicleVariant> variants;
        if (local) {
            variants = vehicleVariantRepository.findByDeletedFalseAndLocalTrueAndLocalPriceIdNotNullOrderByCreatedAtDesc();
        } else if (outstation) {
            variants = vehicleVariantRepository.findByDeletedFalseAndOutstationTrueAndOutstationPriceIdNotNullOrderByCreatedAtDesc();
        } else {
            variants = vehicleVariantRepository.findByDeletedFalse();
        }

        List<Map<String, Object>> resArr = new ArrayList<>();
        for (VehicleVariant val : variants) {
            UUID priceId = local ? val.getLocalPriceId() : val.getOutstationPriceId();
            Price price = priceId == null ? null : priceRepository.findById(priceId).orElse(null);
            if (price == null) {
                continue;
            }
            if (outstation && ("oneway".equals(tripType) || "round".equals(tripType))) {
                Map<String, Object> breakdown = OutstationFareCalculator.calculate(price,
                        onewayPackageRepository.findByPriceIdOrderByFromKmAsc(price.getId()),
                        rentalPackageRepository.findByPriceId(price.getId()),
                        null, tripType, kmVal, promo, days);
                Map<String, Object> returnObj = new LinkedHashMap<>();
                returnObj.put("_id", val.getId());
                returnObj.put("priceId", price.getId());
                returnObj.put("paymentmodesetting", price.getPaymentModeSetting());
                returnObj.put("vehicleTypeId", val.getVehicleTypeId());
                returnObj.put("vehicleDetails", vehicleDetails(val));
                returnObj.put("TotalPrice", breakdown);
                resArr.add(returnObj);
                continue;
            }

            double baseFare = local ? num(price.getBasePrice()) : 0d;
            double driverAllowance = ("round".equals(tripType) && days > 1)
                    ? num(price.getOutstationOneWayRoundDriverAllowance()) : 0d;
            double kilometerPrice = (days > 2 ? num(price.getPricePerDay()) : num(price.getPricePerKm()))
                    * (days > 2 ? days : kmVal);
            double minutePrice = num(price.getPricePerMin()) * (days > 2 ? 0 : timeMin);
            double tollPrice = num(price.getTollTax()) * (outstation ? 0 : tollVal);

            double total = baseFare + num(price.getPriceSurge()) + num(price.getInsurance())
                    + num(price.getPeakTimeCharge()) + num(price.getOthers1()) + num(price.getOthers2())
                    + num(price.getOthers3()) + minutePrice + driverAllowance + kilometerPrice + tollPrice;

            double promoAmt = 0d;
            if (promo != null && !promo.isNull() && !promo.isEmpty()) {
                double discountValue = promo.path("discountValue").asDouble(0);
                double discountAmount = "percentage".equals(promo.path("discountType").asText(""))
                        ? round2((total * discountValue) / 100) : round2(discountValue);
                discountAmount = Math.min(discountAmount, total);
                total -= discountAmount;
                promoAmt = discountAmount;
            }

            double commissionValue = total * num(price.getCommissionPercent()) / 100;
            double subTotal = total + commissionValue;
            double gstPrice = subTotal * num(price.getGstPercent()) / 100;
            double grandTotal = subTotal + gstPrice;

            Map<String, Object> tp = new LinkedHashMap<>();
            tp.put("pricePerKm", price.getPricePerKm());
            tp.put("pricePerMin", price.getPricePerMin());
            tp.put("priceSurge", price.getPriceSurge());
            tp.put("insurance", price.getInsurance());
            tp.put("basePrice", baseFare);
            tp.put("tollTax", price.getTollTax());
            tp.put("gstPercentage", price.getGstPercent());
            tp.put("commissionper", price.getCommissionPercent());
            tp.put("kilometerPrice", kilometerPrice);
            tp.put("minutePrice", minutePrice);
            tp.put("tollPrice", tollPrice);
            tp.put("total", total);
            tp.put("subtotal", subTotal);
            tp.put("commission", commissionValue);
            tp.put("gstprice", gstPrice);
            tp.put("km", String.format("%.2f", kmVal));
            tp.put("grandTotal", String.format("%.2f", grandTotal));
            tp.put("driverAllowance", driverAllowance);
            tp.put("otherFees", num(price.getOthers1()) + num(price.getOthers2()) + num(price.getOthers3()));
            tp.put("promoAmt", -promoAmt);

            Map<String, Object> returnObj = new LinkedHashMap<>();
            returnObj.put("_id", val.getId());
            returnObj.put("priceId", price.getId());
            returnObj.put("paymentmodesetting", price.getPaymentModeSetting());
            returnObj.put("vehicleTypeId", val.getVehicleTypeId());
            returnObj.put("vehicleDetails", vehicleDetails(val));
            returnObj.put("TotalPrice", tp);
            resArr.add(returnObj);
        }
        if (resArr.isEmpty()) {
            throw new ApiException(400, "No vehicles found");
        }
        return resArr;
    }

    @Transactional(readOnly = true)
    public Object getOutstationPriceForVehicle(String id, String tripType, String km, String bookingDate,
                                               String endDate, String packageId, JsonNode promo) {
        UUID vid = toUuid(id);
        if (vid == null) {
            throw new ApiException(400, "Invalid price ID");
        }
        VehicleVariant variant = vehicleVariantRepository.findByIdAndDeletedFalse(vid)
                .orElseThrow(() -> new ApiException(404, "Price details not found"));
        Price price = variant.getOutstationPriceId() == null ? null
                : priceRepository.findById(variant.getOutstationPriceId()).orElse(null);
        if (price == null) {
            // legacy calculateOutstationFare tolerates {} and returns a breakdown of zeros
            throw new ApiException(404, "Price details not found");
        }
        double days = daysBetween(bookingDate, endDate);
        Map<String, Object> breakdown = OutstationFareCalculator.calculate(price,
                onewayPackageRepository.findByPriceIdOrderByFromKmAsc(price.getId()),
                rentalPackageRepository.findByPriceId(price.getId()),
                packageId, tripType, km == null ? 0 : parseDouble(km), promo, days);
        return breakdown;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRentalVehicles() {
        List<VehicleVariant> variants =
                vehicleVariantRepository.findByDeletedFalseAndOutstationTrueAndOutstationPriceIdNotNullOrderByCreatedAtDesc();
        if (variants.isEmpty()) {
            throw new ApiException(404, "No rental vehicles found");
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (VehicleVariant val : variants) {
            Price price = val.getOutstationPriceId() == null ? null
                    : priceRepository.findById(val.getOutstationPriceId()).orElse(null);
            List<Map<String, Object>> packages = new ArrayList<>();
            if (price != null) {
                for (PriceRentalPackage pkg : rentalPackageRepository.findByPriceId(price.getId())) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("_id", pkg.getId());
                    pm.put("durationType", pkg.getDurationType());
                    pm.put("durationValue", pkg.getDurationValue());
                    pm.put("kmLimit", pkg.getKmLimit());
                    pm.put("baseFare", pkg.getBaseFare());
                    packages.add(pm);
                }
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("_id", val.getId());
            obj.put("vehicleTypeId", val.getVehicleTypeId());
            obj.put("vehicleDetails", vehicleDetails(val));
            obj.put("rentalPackages", packages);
            res.add(obj);
        }
        return res;
    }

    /* --------------------------------------------------------------------- exports */

    @Transactional(readOnly = true)
    public ResponseEntity<?> vehicleTypeExport(String fileType, String search, String vehicleTypeId) {
        String s = search == null ? null : search.trim();
        List<VehicleType> rows = vehicleTypeRepository.findByDeletedFalse().stream()
                .filter(v -> s == null || s.isEmpty()
                        || contains(v.getType(), s) || contains(v.getLabel(), s) || contains(v.getDescription(), s))
                .filter(v -> vehicleTypeId == null || vehicleTypeId.isBlank()
                        || v.getId().toString().equals(vehicleTypeId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        List<String> columns = List.of("Type", "Label", "Description", "Status");
        List<Map<String, String>> data = rows.stream().map(v -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Type", nz(v.getType()));
            m.put("Label", nz(v.getLabel()));
            m.put("Description", nz(v.getDescription()));
            m.put("Status", v.isHided() ? "Hidden" : "Visible");
            return m;
        }).toList();
        return TabularExportWriter.write(fileType, "vehicle_types", "Vehicle Type Report", columns, data);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> vehicleVariantExport(String fileType, String search, String vehicleTypeId, String tripTypeId) {
        String s = search == null ? null : search.trim();
        UUID vtId = toUuid(vehicleTypeId);
        List<VehicleVariant> rows = vehicleVariantRepository.findByDeletedFalse().stream()
                .filter(v -> s == null || s.isEmpty() || contains(v.getType(), s) || contains(v.getLabel(), s))
                .filter(v -> vtId == null || vtId.equals(v.getVehicleTypeId()))
                .filter(v -> {
                    if ("isLocal".equals(tripTypeId)) {
                        return v.isLocal();
                    }
                    if ("isOutstation".equals(tripTypeId)) {
                        return v.isOutstation();
                    }
                    return true;
                })
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        List<String> columns = List.of("Vehicle Type", "Variant", "Description", "Seats", "Local", "OutStation");
        List<Map<String, String>> data = rows.stream().map(v -> {
            String typeLabel = v.getVehicleTypeId() == null ? "-"
                    : vehicleTypeRepository.findById(v.getVehicleTypeId()).map(VehicleType::getLabel).orElse("-");
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Vehicle Type", nz(typeLabel));
            m.put("Variant", nz(v.getLabel()));
            m.put("Description", nz(v.getDescription()));
            m.put("Seats", String.valueOf(v.getSeats() == null ? 0 : v.getSeats()));
            m.put("Local", v.isLocal() ? "Yes" : "No");
            m.put("OutStation", v.isOutstation() ? "Yes" : "No");
            return m;
        }).toList();
        return TabularExportWriter.write(fileType, "vehicle_variants", "Vehicle Variant Report", columns, data);
    }

    /* --------------------------------------------------------------------- helpers */

    private Map<String, Object> priceWithPackages(Price p) {
        return priceMap(p);
    }

    private Map<String, Object> vehicleDetails(VehicleVariant val) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("_id", val.getId());
        d.put("name", val.getLabel());
        d.put("desc", val.getDescription());
        d.put("image", val.getImage());
        d.put("seat", val.getSeats() == null ? 0 : val.getSeats());
        return d;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static double num(java.math.BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static int parseInt(String s) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static double daysBetween(String start, String end) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            return 0d;
        }
        try {
            java.time.Instant a = java.time.Instant.parse(start);
            java.time.Instant b = java.time.Instant.parse(end);
            return Math.ceil((b.toEpochMilli() - a.toEpochMilli()) / (24.0 * 60 * 60 * 1000));
        } catch (Exception e) {
            return 0d;
        }
    }
}
