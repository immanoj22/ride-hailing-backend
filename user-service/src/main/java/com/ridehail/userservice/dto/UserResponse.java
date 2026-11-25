package com.ridehail.userservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import com.ridehail.userservice.entity.User;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wire shape of a Mongoose "Users" document (modals/user.schema.js), as returned (fully, with
 * "Address" populated) by findOneUser/updateUser in service/user.services.js. Field keys mirror
 * the original schema exactly - several are NOT camelCase in the original
 * ("termsandserviceaccept", "otpexpire", "otpverify", "tripid", capitalized "Address") and are
 * pinned via {@code @JsonProperty} rather than "fixed" to Java conventions, since mobile clients
 * depend on the exact wire keys.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    @JsonProperty("_id")
    private UUID id;

    private String firstname;

    private String lastname;

    private String fcm;

    private Boolean socketstatus;

    private String state;

    private String city;

    @JsonProperty("termsandserviceaccept")
    private Boolean termsandserviceaccept;

    private String gender;

    private String email;

    private String profile;

    private String mobile;

    @JsonProperty("delete")
    private Boolean delete;

    private String userid;

    private String otp;

    @JsonProperty("otpexpire")
    private Long otpexpire;

    @JsonProperty("otpverify")
    private Boolean otpverify;

    @JsonProperty("Address")
    private List<AddressResponse> address;

    private String socketId;

    private AddressResponse.GeoPoint currentLocation;

    @JsonProperty("tripid")
    @JsonRawValue
    private String tripid;

    @JsonRawValue
    private String emergencyContacts;

    private String fullname;

    private Boolean profileSkip;

    private Preferences preferences;

    private Wallet wallet;

    private String referralCode;

    private UUID referredBy;

    @JsonRawValue
    private String referrals;

    private ReferralReward referralReward;

    private BigDecimal walletBalance;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** Only populated by get_user_profile_by_user when {@code isRideCheck=true} - not a persisted
     *  User field, mirrors the original handler attaching {@code user.latestRide} ad hoc. */
    private com.fasterxml.jackson.databind.JsonNode latestRide;

    public com.fasterxml.jackson.databind.JsonNode getLatestRide() {
        return latestRide;
    }

    public void setLatestRide(com.fasterxml.jackson.databind.JsonNode latestRide) {
        this.latestRide = latestRide;
    }

    public static UserResponse from(User user, List<AddressResponse> addresses) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setFirstname(user.getFirstname());
        response.setLastname(user.getLastname());
        response.setFcm(user.getFcm());
        response.setSocketstatus(user.getSocketstatus());
        response.setState(user.getState());
        response.setCity(user.getCity());
        response.setTermsandserviceaccept(user.getTermsAndServiceAccept());
        response.setGender(user.getGender());
        response.setEmail(user.getEmail());
        response.setProfile(user.getProfile());
        response.setMobile(user.getMobile());
        response.setDelete(user.getDeleted());
        response.setUserid(user.getUserid());
        response.setOtp(user.getOtp());
        response.setOtpexpire(user.getOtpExpire());
        response.setOtpverify(user.getOtpVerify());
        response.setAddress(addresses);
        response.setSocketId(user.getSocketId());
        if (user.getCurrentLocationLat() != null && user.getCurrentLocationLng() != null) {
            response.setCurrentLocation(new AddressResponse.GeoPoint("Point",
                    List.of(user.getCurrentLocationLng(), user.getCurrentLocationLat())));
        }
        response.setTripid(user.getTripIds());
        response.setEmergencyContacts(user.getEmergencyContacts());
        response.setFullname(user.getFullname());
        response.setProfileSkip(user.getProfileSkip());
        response.setPreferences(new Preferences(
                user.getAllowSmsInvoice(), user.getAllowWhatsAppUpdates(),
                user.getAllowEmailInvoice(), user.getAllowMobileNotification()));
        response.setWallet(new Wallet(user.getWalletCurrentAmount(), user.getWalletLastUpdated()));
        response.setReferralCode(user.getReferralCode());
        response.setReferredBy(user.getReferredBy());
        response.setReferrals(user.getReferrals());
        response.setReferralReward(new ReferralReward(
                user.getReferralRewardType() == null ? "" : user.getReferralRewardType(),
                user.getReferralRewardAmount(),
                user.getReferralRewardDiscountPercent(),
                user.getReferralRewardRemainingTrips(),
                user.getReferralRewardUsedRideIds(),
                user.getReferralRewardGrantedAt()));
        response.setWalletBalance(user.getWalletBalance());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Preferences {
        private Boolean allowSmsInvoice;
        private Boolean allowWhatsAppUpdates;
        private Boolean allowEmailInvoice;
        private Boolean allowMobileNotification;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Wallet {
        private BigDecimal currentAmount;
        private OffsetDateTime lastUpdated;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferralReward {
        private String type;
        private BigDecimal amount;
        private BigDecimal discountPercent;
        private Integer remainingTrips;
        @JsonRawValue
        private String usedRideIds;
        private OffsetDateTime grantedAt;
    }
}
