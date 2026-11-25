package com.ridehail.adminservice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.adminservice.dto.AddCouponRequest;
import com.ridehail.adminservice.dto.ConsumeCouponRequest;
import com.ridehail.adminservice.dto.GetUserCouponsRequest;
import com.ridehail.adminservice.dto.SendCouponRequest;
import com.ridehail.adminservice.dto.UpdateCouponRequest;
import com.ridehail.adminservice.service.CouponService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.Valid;

/**
 * Direct port of the //coupon routes in Admin-service/routes/admin.routes.js (mounted at
 * /admin-apis). Paths, methods and response messages are preserved verbatim.
 */
@RestController
@RequestMapping("/admin-apis")
public class CouponController {

    private final CouponService couponService;
    private final ApiResponseService apiResponseService;

    public CouponController(CouponService couponService, ApiResponseService apiResponseService) {
        this.couponService = couponService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping(value = "/add-coupon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addCoupon(@Valid @ModelAttribute AddCouponRequest request,
                                       @RequestParam(value = "image", required = false) MultipartFile image) {
        return apiResponseService.ok("Coupon created successfully", couponService.addCoupon(request, image));
    }

    @PostMapping(value = "/update-coupon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateCoupon(@Valid @ModelAttribute UpdateCouponRequest request,
                                          @RequestParam(value = "image", required = false) MultipartFile image) {
        boolean deleteRequested = "true".equalsIgnoreCase(String.valueOf(request.getDelete()));
        var data = couponService.updateCoupon(request, image);
        return apiResponseService.ok(deleteRequested ? "Coupon deleted successfully" : "Coupon updated successfully", data);
    }

    @GetMapping("/get-all-coupon")
    public ResponseEntity<?> getCouponList(@RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "limit", required = false) Integer limit,
                                           @RequestParam(value = "search", required = false) String search) {
        return apiResponseService.ok("Coupons fetched successfully", couponService.getCouponList(page, limit, search));
    }

    @GetMapping("/get-coupon/{id}")
    public ResponseEntity<?> getCoupon(@PathVariable("id") String id) {
        return apiResponseService.ok("Coupon fetched successfully", couponService.getCoupon(id));
    }

    @PostMapping("/send-coupon")
    public ResponseEntity<?> sendCoupon(@Valid @RequestBody SendCouponRequest request) {
        return apiResponseService.ok(couponService.sendCoupon(request), java.util.Map.of());
    }

    @PostMapping("/get-user-coupons")
    public ResponseEntity<?> getUserCoupons(@RequestBody GetUserCouponsRequest request) {
        Object coupons = couponService.getUserCoupons(request);
        return apiResponseService.ok("Eligible coupons fetched", coupons);
    }

    @PostMapping("/consume-coupon")
    public ResponseEntity<?> consumeCoupon(@RequestBody ConsumeCouponRequest request) {
        CouponService.ConsumeResult result = couponService.consumeCoupon(request);
        return apiResponseService.ok(result.message(), result.data());
    }
}
