package com.ridehail.rideservice.service;

import static com.ridehail.rideservice.service.VehicleCatalogSupport.priceMap;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.requireAdmin;
import static com.ridehail.rideservice.service.VehicleCatalogSupport.toUuid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.dto.AddPriceRequest;
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

/** Port of add_price / price_list / update_price in controller/vehicle.controller.js. */
@Service
public class PriceService {

    private static final List<String> DURATION_ORDER = List.of("hour", "day", "week");

    private final PriceRepository priceRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final PriceOnewayPackageRepository onewayPackageRepository;
    private final PriceRentalPackageRepository rentalPackageRepository;

    public PriceService(PriceRepository priceRepository,
                        VehicleTypeRepository vehicleTypeRepository,
                        VehicleVariantRepository vehicleVariantRepository,
                        PriceOnewayPackageRepository onewayPackageRepository,
                        PriceRentalPackageRepository rentalPackageRepository) {
        this.priceRepository = priceRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.onewayPackageRepository = onewayPackageRepository;
        this.rentalPackageRepository = rentalPackageRepository;
    }

    @Transactional
    public Map<String, Object> addPrice(AddPriceRequest req) {
        requireAdmin();
        String category = req.getCategory();
        UUID vtId = toUuid(req.getVehicleTypeId());
        UUID vvId = toUuid(req.getVehicleVariantId());

        if (vtId != null && vehicleTypeRepository.findByIdAndDeletedFalse(vtId).isEmpty()) {
            throw new ApiException(400, "Invalid vehicleTypeId");
        }
        VehicleVariant variant = null;
        if (vvId != null) {
            variant = vehicleVariantRepository.findByIdAndDeletedFalse(vvId)
                    .orElseThrow(() -> new ApiException(400, "Invalid vehicleVariantId"));
            if ("local".equals(category) && variant.getLocalPriceId() != null) {
                throw new ApiException(400, "Local Price already exists for this vehicle variant");
            }
            if ("outstation".equals(category) && variant.getOutstationPriceId() != null) {
                throw new ApiException(400, "Outstation Price already exists for this vehicle variant");
            }
        }

        if (vtId != null && vvId != null
                && priceRepository.findByVehicleTypeIdAndVehicleVariantIdAndDeletedFalse(vtId, vvId).isPresent()) {
            throw new ApiException(400, "Price record already exists for this category");
        }

        Price price = Price.builder()
                .vehicleTypeId(vtId)
                .vehicleVariantId(vvId)
                .pricePerKm(nz(req.getPricePerKm()))
                .pricePerMin(nz(req.getPricePerMin()))
                .priceSurge(nz(req.getPriceSurge()))
                .basePrice(nz(req.getBasePrice()))
                .pricePerDay(nz(req.getPricePerDay()))
                .insurance(nz(req.getInsurance()))
                .tollTax(nz(req.getTollTax()))
                .mcdTax(nz(req.getMcdTax()))
                .gstPercent(nz(req.getGstPercent()))
                .commissionPercent(nz(req.getCommissionPercent()))
                .peakTimeCharge(nz(req.getPeakTimeCharge()))
                .userCancellationTimeLimitMinutes(nz(req.getUserCancellationTimeLimitMinutes()).intValue())
                .userCancellationCharge(nz(req.getUserCancellationCharge()))
                .waitingTimeLimitMinutes(nz(req.getWaitingTimeLimitMinutes()).intValue())
                .waitingChargePerMin(nz(req.getWaitingChargePerMin()))
                .transitWaitingFeePerMin(nz(req.getTransitWaitingFeePerMin()))
                .others1(nz(req.getOthers1()))
                .others2(nz(req.getOthers2()))
                .others3(nz(req.getOthers3()))
                .paymentModeSetting(req.getPaymentModeSetting() == null
                        ? new String[0] : req.getPaymentModeSetting().toArray(new String[0]))
                .build();

        applyOutstation(price, req.getOutstation());
        Price saved = priceRepository.save(price);

        if (variant != null) {
            if ("local".equals(category)) {
                variant.setLocalPriceId(saved.getId());
            } else if ("outstation".equals(category)) {
                variant.setOutstationPriceId(saved.getId());
            }
            vehicleVariantRepository.save(variant);
        }
        return priceMap(priceRepository.findById(saved.getId()).orElse(saved));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> priceList(String page, String limit, String vehicleTypeId,
                                               String vehicleVariantId, String brand, String delFlag) {
        boolean deleted = "true".equalsIgnoreCase(delFlag);
        Specification<Price> spec = (root, q, cb) -> cb.equal(root.get("deleted"), deleted);
        UUID vtId = toUuid(vehicleTypeId);
        if (vtId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicleTypeId"), vtId));
        }
        UUID vvId = toUuid(vehicleVariantId);
        if (vvId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicleVariantId"), vvId));
        }
        if (brand != null && !brand.isBlank()) {
            String like = "%" + brand.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("brand")), like));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Price> found;
        if (page != null && !page.isBlank() && limit != null && !limit.isBlank()) {
            int p = Integer.parseInt(page);
            int l = Integer.parseInt(limit);
            found = priceRepository.findAll(spec, PageRequest.of(Math.max(p - 1, 0), l, sort)).getContent();
        } else {
            found = priceRepository.findAll(spec, sort);
        }
        if (found.isEmpty()) {
            throw new ApiException(400, "Failed to fetch prices");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Price p : found) {
            Map<String, Object> m = priceMap(p);
            m.put("vehicleTypeId", p.getVehicleTypeId() == null ? null
                    : vehicleTypeRepository.findById(p.getVehicleTypeId())
                        .map(VehicleCatalogSupport::vehicleTypeMap).orElse(null));
            m.put("vehicleVariantId", p.getVehicleVariantId() == null ? null
                    : vehicleVariantRepository.findById(p.getVehicleVariantId())
                        .map(v -> VehicleCatalogSupport.vehicleVariantMap(v, null, null, null)).orElse(null));
            result.add(m);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updatePrice(JsonNode body) {
        requireAdmin();
        UUID id = toUuid(text(body, "_id"));
        if (id == null) {
            throw new ApiException(400, "Invalid _id");
        }
        if (body.size() <= 1) {
            throw new ApiException(400, "Nothing to update");
        }
        UUID vtId = toUuid(text(body, "vehicleTypeId"));
        UUID vvId = toUuid(text(body, "vehicleVariantId"));
        if (vtId == null || vvId == null) {
            throw new ApiException(400, "Invalid vehicleTypeId / vehicleVariantId");
        }
        VehicleType vt = vehicleTypeRepository.findByIdAndDeletedFalse(vtId)
                .orElseThrow(() -> new ApiException(400, "Vehicle type not found"));
        VehicleVariant vv = vehicleVariantRepository.findByIdAndDeletedFalse(vvId)
                .orElseThrow(() -> new ApiException(400, "Vehicle variant not found"));

        Price price = priceRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Price not found or update failed"));

        price.setVehicleTypeId(vtId);
        price.setVehicleVariantId(vvId);
        if (has(body, "brand")) {
            price.setBrand(body.get("brand").asText(""));
        }
        setNum(body, "pricePerKm", price::setPricePerKm);
        setNum(body, "pricePerMin", price::setPricePerMin);
        setNum(body, "priceSurge", price::setPriceSurge);
        setNum(body, "basePrice", price::setBasePrice);
        setNum(body, "pricePerDay", price::setPricePerDay);
        setNum(body, "pricePerExtraKm", price::setPricePerExtraKm);
        setNum(body, "pricePerExtraMin", price::setPricePerExtraMin);
        setNum(body, "insurance", price::setInsurance);
        setNum(body, "tollTax", price::setTollTax);
        setNum(body, "mcdTax", price::setMcdTax);
        setNum(body, "gstPercent", price::setGstPercent);
        setNum(body, "commissionPercent", price::setCommissionPercent);
        setNum(body, "peakTimeCharge", price::setPeakTimeCharge);
        setInt(body, "userCancellationTimeLimitMinutes", price::setUserCancellationTimeLimitMinutes);
        setNum(body, "userCancellationCharge", price::setUserCancellationCharge);
        setInt(body, "waitingTimeLimitMinutes", price::setWaitingTimeLimitMinutes);
        setNum(body, "waitingChargePerMin", price::setWaitingChargePerMin);
        setNum(body, "transitWaitingFeePerMin", price::setTransitWaitingFeePerMin);
        setNum(body, "others1", price::setOthers1);
        setNum(body, "others2", price::setOthers2);
        setNum(body, "others3", price::setOthers3);
        setNum(body, "adminCommissionPrice", price::setAdminCommissionPrice);
        setNum(body, "driverCommissionPrice", price::setDriverCommissionPrice);
        if (has(body, "delete")) {
            price.setDeleted(body.get("delete").asBoolean(false));
        }
        if (has(body, "paymentModeSetting")) {
            JsonNode pms = body.get("paymentModeSetting");
            List<String> modes = new ArrayList<>();
            if (pms.isArray()) {
                pms.forEach(n -> modes.add(n.asText()));
            } else if (pms.isTextual()) {
                for (String s : pms.asText().split(",")) {
                    if (!s.trim().isEmpty()) {
                        modes.add(s.trim());
                    }
                }
            }
            price.setPaymentModeSetting(modes.toArray(new String[0]));
        }
        if (has(body, "outstation")) {
            applyOutstation(price, body.get("outstation"));
        }
        Price saved = priceRepository.save(price);
        return priceMap(priceRepository.findById(saved.getId()).orElse(saved));
    }

    /* --------------------------------------------------------------------- helpers */

    private void applyOutstation(Price price, JsonNode outstation) {
        if (outstation == null || outstation.isNull() || !outstation.isObject()) {
            return;
        }
        if (outstation.has("extraKmFare")) {
            price.setOutstationExtraKmFare(dec(outstation.get("extraKmFare")));
        }
        if (outstation.has("extraMinuteFare")) {
            price.setOutstationExtraMinuteFare(dec(outstation.get("extraMinuteFare")));
        }
        if (outstation.has("waitingCharge")) {
            price.setOutstationWaitingCharge(dec(outstation.get("waitingCharge")));
        }
        if (outstation.has("cancelCharge")) {
            price.setOutstationCancelCharge(dec(outstation.get("cancelCharge")));
        }
        JsonNode oneWayRound = outstation.get("oneWayRound");
        if (oneWayRound != null && oneWayRound.isObject()) {
            if (oneWayRound.has("driverAllowance")) {
                price.setOutstationOneWayRoundDriverAllowance(dec(oneWayRound.get("driverAllowance")));
            }
            JsonNode packages = oneWayRound.get("packages");
            if (packages != null && packages.isArray()) {
                price.getOnewayPackages().clear();
                List<PriceOnewayPackage> list = new ArrayList<>();
                for (JsonNode p : packages) {
                    list.add(PriceOnewayPackage.builder()
                            .price(price)
                            .fromKm(dec(p.get("fromKm")))
                            .toKm(dec(p.get("toKm")))
                            .baseFare(dec(p.get("baseFare")))
                            .build());
                }
                list.sort(Comparator.comparing((PriceOnewayPackage x) -> x.getFromKm())
                        .thenComparing(PriceOnewayPackage::getToKm));
                price.getOnewayPackages().addAll(list);
            }
        }
        JsonNode rental = outstation.get("rental");
        if (rental != null && rental.isObject()) {
            JsonNode packages = rental.get("packages");
            if (packages != null && packages.isArray()) {
                price.getRentalPackages().clear();
                List<PriceRentalPackage> list = new ArrayList<>();
                for (JsonNode p : packages) {
                    String durationType = p.hasNonNull("durationType") ? p.get("durationType").asText("hour") : "hour";
                    list.add(PriceRentalPackage.builder()
                            .price(price)
                            .durationType(durationType)
                            .durationValue(dec(p.get("durationValue")))
                            .kmLimit(dec(p.get("kmLimit")))
                            .baseFare(dec(p.get("baseFare")))
                            .build());
                }
                list.sort(Comparator
                        .comparingInt((PriceRentalPackage x) -> orderOf(x.getDurationType()))
                        .thenComparing(PriceRentalPackage::getDurationValue));
                price.getRentalPackages().addAll(list);
            }
        }
    }

    private static int orderOf(String durationType) {
        int i = DURATION_ORDER.indexOf(durationType);
        return i < 0 ? 99 : i + 1;
    }

    private static boolean has(JsonNode body, String field) {
        return body.has(field) && !body.get(field).isNull();
    }

    private static String text(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static void setNum(JsonNode body, String field, java.util.function.Consumer<BigDecimal> setter) {
        if (has(body, field)) {
            setter.accept(dec(body.get(field)));
        }
    }

    private static void setInt(JsonNode body, String field, java.util.function.Consumer<Integer> setter) {
        if (has(body, field)) {
            setter.accept((int) dec(body.get(field)).doubleValue());
        }
    }

    private static BigDecimal dec(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.asText("0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
