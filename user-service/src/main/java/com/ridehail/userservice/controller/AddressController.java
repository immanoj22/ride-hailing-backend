package com.ridehail.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.userservice.dto.AddAddressRequest;
import com.ridehail.userservice.dto.UpdateAddressRequest;
import com.ridehail.userservice.service.AddressService;
import com.ridehail.userservice.service.AddressService.UpdateAddressResult;
import com.ridehail.userservice.util.RequestMetaUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * "address-book" bucket - direct port of routes/user.routes.js's add-address/update-address
 * routes (mounted under "/user-apis" by server.js's {@code app.use("/user-apis", routes)}). No
 * business logic here - see {@link AddressService}.
 */
@RestController
@RequestMapping("/user-apis")
public class AddressController {

    private final AddressService addressService;
    private final ApiResponseService apiResponseService;

    public AddressController(AddressService addressService, ApiResponseService apiResponseService) {
        this.addressService = addressService;
        this.apiResponseService = apiResponseService;
    }

    /** POST /add-address. */
    @PostMapping("/add-address")
    public ResponseEntity<?> addAddress(@Valid @RequestBody AddAddressRequest request, HttpServletRequest httpRequest) {
        RequestMetaUtil.RequestMeta requestMeta = RequestMetaUtil.extract(httpRequest);
        var data = addressService.addAddress(request, requestMeta);
        return apiResponseService.ok("Address added successfully", data);
    }

    /** POST /update-address. */
    @PostMapping("/update-address")
    public ResponseEntity<?> updateAddress(@Valid @RequestBody UpdateAddressRequest request, HttpServletRequest httpRequest) {
        RequestMetaUtil.RequestMeta requestMeta = RequestMetaUtil.extract(httpRequest);
        UpdateAddressResult result = addressService.updateAddress(request, requestMeta);
        return apiResponseService.ok(result.message(), result.data());
    }
}
