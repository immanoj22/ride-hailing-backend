package com.ridehail.driverservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire-compatible reproduction of the legacy Mongoose "driver" document as returned by
 * {@code driver.toObject()} in driver.controller.js (register/profile/kyc/bank/etc responses).
 * Field names mirror modals/driver.schema.js exactly, including the Mongo-ism {@code _id} and the
 * "delete" field (Java entity name {@code deleted}, Mongoose/wire name "delete" - see
 * {@link com.ridehail.driverservice.entity.Driver}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverResponse {

    @JsonProperty("_id")
    private String id;

    private String mobile;
    private String email;
    private String firstName;
    private String lastName;
    private String gender;
    private String state;
    private String city;
    private boolean profileCompleted;
    private String fcmToken;
    private String socketId;
    private boolean socketOnline;
    private String status;
    private boolean kycCompleted;
    private String kycStatus;
    private String profileImage;
    private boolean unverifiedDriver;
    private List<DocumentResponse> documents;
    private String vehicleNumber;
    private String chassisNumber;
    private String engineNumber;
    private String fuelType;
    private String vehicleType;
    private String vehicleVariant;
    private String driverStatus;
    private OffsetDateTime lastOnlineAt;
    private GeoPointResponse currentLocation;
    private JsonNode preferredLocations;
    private boolean onRide;

    @JsonProperty("delete")
    private boolean delete;

    private String deleteReason;
    private List<String> trips;
    private OffsetDateTime lastTripAt;
    private List<EmergencyContactResponse> emergencyContacts;
    private BankDetailsResponse bankDetails;
    private String referralCode;
    private String referredBy;

    @JsonProperty("isBlocked")
    private boolean isBlocked;

    private WalletResponse wallet;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String fullName;

    /** Only present on getDriverById/getDriverDetails (vehicle-service price-list lookup). */
    private List<JsonNode> vehicleDetails;

    /** Only present on get_profile when isRideCheck was requested and an active ride exists. */
    private JsonNode currentRide;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getSocketId() {
        return socketId;
    }

    public void setSocketId(String socketId) {
        this.socketId = socketId;
    }

    public boolean isSocketOnline() {
        return socketOnline;
    }

    public void setSocketOnline(boolean socketOnline) {
        this.socketOnline = socketOnline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isKycCompleted() {
        return kycCompleted;
    }

    public void setKycCompleted(boolean kycCompleted) {
        this.kycCompleted = kycCompleted;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public boolean isUnverifiedDriver() {
        return unverifiedDriver;
    }

    public void setUnverifiedDriver(boolean unverifiedDriver) {
        this.unverifiedDriver = unverifiedDriver;
    }

    public List<DocumentResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentResponse> documents) {
        this.documents = documents;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getChassisNumber() {
        return chassisNumber;
    }

    public void setChassisNumber(String chassisNumber) {
        this.chassisNumber = chassisNumber;
    }

    public String getEngineNumber() {
        return engineNumber;
    }

    public void setEngineNumber(String engineNumber) {
        this.engineNumber = engineNumber;
    }

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleVariant() {
        return vehicleVariant;
    }

    public void setVehicleVariant(String vehicleVariant) {
        this.vehicleVariant = vehicleVariant;
    }

    public String getDriverStatus() {
        return driverStatus;
    }

    public void setDriverStatus(String driverStatus) {
        this.driverStatus = driverStatus;
    }

    public OffsetDateTime getLastOnlineAt() {
        return lastOnlineAt;
    }

    public void setLastOnlineAt(OffsetDateTime lastOnlineAt) {
        this.lastOnlineAt = lastOnlineAt;
    }

    public GeoPointResponse getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(GeoPointResponse currentLocation) {
        this.currentLocation = currentLocation;
    }

    public JsonNode getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(JsonNode preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public boolean isOnRide() {
        return onRide;
    }

    public void setOnRide(boolean onRide) {
        this.onRide = onRide;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    public List<String> getTrips() {
        return trips;
    }

    public void setTrips(List<String> trips) {
        this.trips = trips;
    }

    public OffsetDateTime getLastTripAt() {
        return lastTripAt;
    }

    public void setLastTripAt(OffsetDateTime lastTripAt) {
        this.lastTripAt = lastTripAt;
    }

    public List<EmergencyContactResponse> getEmergencyContacts() {
        return emergencyContacts;
    }

    public void setEmergencyContacts(List<EmergencyContactResponse> emergencyContacts) {
        this.emergencyContacts = emergencyContacts;
    }

    public BankDetailsResponse getBankDetails() {
        return bankDetails;
    }

    public void setBankDetails(BankDetailsResponse bankDetails) {
        this.bankDetails = bankDetails;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public WalletResponse getWallet() {
        return wallet;
    }

    public void setWallet(WalletResponse wallet) {
        this.wallet = wallet;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public List<JsonNode> getVehicleDetails() {
        return vehicleDetails;
    }

    public void setVehicleDetails(List<JsonNode> vehicleDetails) {
        this.vehicleDetails = vehicleDetails;
    }

    public JsonNode getCurrentRide() {
        return currentRide;
    }

    public void setCurrentRide(JsonNode currentRide) {
        this.currentRide = currentRide;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentResponse {
        @JsonProperty("key")
        private String key;
        private String title;
        private String status;
        private JsonNode files;
        private JsonNode fileStatus;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public JsonNode getFiles() {
            return files;
        }

        public void setFiles(JsonNode files) {
            this.files = files;
        }

        public JsonNode getFileStatus() {
            return fileStatus;
        }

        public void setFileStatus(JsonNode fileStatus) {
            this.fileStatus = fileStatus;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmergencyContactResponse {
        @JsonProperty("_id")
        private String id;
        private String name;
        private String relation;
        private String phone;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRelation() {
            return relation;
        }

        public void setRelation(String relation) {
            this.relation = relation;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankDetailsResponse {
        private String accountNumber;
        private String ifsc;
        private String holderName;
        private String bankName;
        private String branch;
        private String upiId;
        private String upiQrImage;

        public String getAccountNumber() {
            return accountNumber;
        }

        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
        }

        public String getIfsc() {
            return ifsc;
        }

        public void setIfsc(String ifsc) {
            this.ifsc = ifsc;
        }

        public String getHolderName() {
            return holderName;
        }

        public void setHolderName(String holderName) {
            this.holderName = holderName;
        }

        public String getBankName() {
            return bankName;
        }

        public void setBankName(String bankName) {
            this.bankName = bankName;
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }

        public String getUpiId() {
            return upiId;
        }

        public void setUpiId(String upiId) {
            this.upiId = upiId;
        }

        public String getUpiQrImage() {
            return upiQrImage;
        }

        public void setUpiQrImage(String upiQrImage) {
            this.upiQrImage = upiQrImage;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GeoPointResponse {
        private String type = "Point";
        private List<Double> coordinates;

        public GeoPointResponse() {
        }

        public GeoPointResponse(Double lng, Double lat) {
            this.coordinates = List.of(lng, lat);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Double> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Double> coordinates) {
            this.coordinates = coordinates;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WalletResponse {
        @JsonProperty("_id")
        private String id;
        private String driverId;
        private BigDecimal currentAmount;
        private BigDecimal claimAmount;
        private OffsetDateTime lastClaimDate;
        private String walletStatus;
        private BigDecimal pendingAmount;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDriverId() {
            return driverId;
        }

        public void setDriverId(String driverId) {
            this.driverId = driverId;
        }

        public BigDecimal getCurrentAmount() {
            return currentAmount;
        }

        public void setCurrentAmount(BigDecimal currentAmount) {
            this.currentAmount = currentAmount;
        }

        public BigDecimal getClaimAmount() {
            return claimAmount;
        }

        public void setClaimAmount(BigDecimal claimAmount) {
            this.claimAmount = claimAmount;
        }

        public OffsetDateTime getLastClaimDate() {
            return lastClaimDate;
        }

        public void setLastClaimDate(OffsetDateTime lastClaimDate) {
            this.lastClaimDate = lastClaimDate;
        }

        public String getWalletStatus() {
            return walletStatus;
        }

        public void setWalletStatus(String walletStatus) {
            this.walletStatus = walletStatus;
        }

        public BigDecimal getPendingAmount() {
            return pendingAmount;
        }

        public void setPendingAmount(BigDecimal pendingAmount) {
            this.pendingAmount = pendingAmount;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public OffsetDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
