package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.dto.CreateRazorpayOrderRequest;
import com.ridehail.rideservice.dto.VerifyRazorpayPaymentRequest;
import com.ridehail.rideservice.service.RazorpayService;

import jakarta.validation.Valid;

/**
 * Ports ride.controller.js: create_razorpay_order, verify_razorpay_payment.
 * Both routes are guarded by authUserMiddleware(user) in ride.routes.js - enforced here against
 * {@link AuthContext} (also allowing in-app, matching authUserMiddleware's {@code req.auth_user === 'inapp'} branch).
 */
@RestController
@RequestMapping("/ride-apis")
public class RazorpayController {

    private final RazorpayService service;
    private final ApiResponseService apiResponseService;

    public RazorpayController(RazorpayService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    private void requireUser() {
        AuthUserType type = AuthContext.current() == null ? null : AuthContext.current().authUserType();
        if (type != AuthUserType.USER && type != AuthUserType.IN_APP) {
            throw new ApiException(StatusCode.FORBIDDEN, "This is only user API.");
        }
    }

    @PostMapping("/create-razorpay-order")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateRazorpayOrderRequest body) {
        requireUser();
        return apiResponseService.ok("Razorpay order created successfully",
                service.createOrder(body.getAmount(), body.getCurrency()));
    }

    @PostMapping("/verify-razorpay-payment")
    public ResponseEntity<?> verifyPayment(@Valid @RequestBody VerifyRazorpayPaymentRequest body) {
        requireUser();
        return apiResponseService.ok("Razorpay payment verified successfully",
                service.verifyPayment(body.getRazorpayOrderId(), body.getRazorpayPaymentId(), body.getRazorpaySignature()));
    }
}
