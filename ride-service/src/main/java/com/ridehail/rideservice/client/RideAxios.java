package com.ridehail.rideservice.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.security.InternalServiceClient;

/**
 * Direct port of utills/ride.axios.js - every service-to-service call the ride lifecycle flows
 * make. URLs mirror the original exactly (base URL from application.yml ridehail.services.*, path
 * suffix verbatim from ride.axios.js). All calls go through {@link InternalServiceClient} so the
 * in-app auth headers + response decryption are handled the same way axiosFunc handled them.
 */
@Component
public class RideAxios {

    private final InternalServiceClient client;
    private final String userUrl;
    private final String driverUrl;
    private final String adminUrl;

    public RideAxios(InternalServiceClient client,
                     @Value("${ridehail.services.user-url}") String userUrl,
                     @Value("${ridehail.services.driver-url}") String driverUrl,
                     @Value("${ridehail.services.admin-url}") String adminUrl) {
        this.client = client;
        this.userUrl = userUrl;
        this.driverUrl = driverUrl;
        this.adminUrl = adminUrl;
    }

    public JsonNode getUserById(Object id) {
        return client.get(userUrl + "/profile/" + id);
    }

    public JsonNode getDriverById(Object id) {
        return client.get(driverUrl + "/profile/" + id);
    }

    public JsonNode getAdmin() {
        return client.get(adminUrl + "/get-admin");
    }

    public JsonNode getAdminById(Object id) {
        return client.get(adminUrl + "/get-admin/" + id);
    }

    public JsonNode getSettings() {
        return client.get(adminUrl + "/get-settings");
    }

    public JsonNode getCouponById(Object id) {
        return client.get(adminUrl + "/get-coupon/" + id);
    }

    public JsonNode getNearByDrivers(Object payload) {
        return client.post(driverUrl + "/nearby-drivers", payload);
    }

    /** utills/ride.axios.js getNearByOutstationDrivers - POST DRIVER_SERVICE_URL/nearby-outstation-drivers. */
    public JsonNode getNearByOutstationDrivers(Object payload) {
        return client.post(driverUrl + "/nearby-outstation-drivers", payload);
    }

    public JsonNode updateDriverWallet(Object data) {
        return client.post(driverUrl + "/update-wallet", data);
    }

    public JsonNode updateUserWallet(Object data) {
        return client.post(userUrl + "/update-wallet", data);
    }

    public JsonNode consumeReferralDiscount(Object data) {
        return client.post(userUrl + "/consume-referral-discount", data);
    }

    public JsonNode createCouponUsage(Object data) {
        return client.post(adminUrl + "/consume-coupon", data);
    }

    public JsonNode getUsersByIds(Object data) {
        return client.post(userUrl + "/get-users-details", data);
    }

    public JsonNode getDriversByIds(Object data) {
        return client.post(driverUrl + "/getMultipleDriversById", data);
    }

    public JsonNode updateDriverProfile(Object data) {
        return client.post(driverUrl + "/update-profile", data);
    }

    public JsonNode updateDriverLocation(Object data) {
        return client.post(driverUrl + "/update-location", data);
    }

    /** ride.controller.js uses raw axiosFunc({url: DRIVER_SERVICE_URL + "/update-profile"}). */
    public JsonNode driverUpdateProfileRaw(Map<String, Object> data) {
        return client.post(driverUrl + "/update-profile", data);
    }
}
