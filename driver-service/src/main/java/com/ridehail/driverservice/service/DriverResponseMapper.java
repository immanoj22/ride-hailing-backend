package com.ridehail.driverservice.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ridehail.driverservice.dto.DriverResponse;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverDocument;
import com.ridehail.driverservice.entity.DriverEmergencyContact;
import com.ridehail.driverservice.entity.DriverWallet;
import com.ridehail.driverservice.repository.DriverDocumentRepository;
import com.ridehail.driverservice.repository.DriverEmergencyContactRepository;
import com.ridehail.driverservice.repository.DriverWalletRepository;

/**
 * Builds the wire-compatible {@link DriverResponse} (equivalent of the legacy
 * {@code driver.toObject()} payload) from the split entity/child-table representation used by
 * this service (see {@link Driver}'s class-level notes on documents/emergencyContacts/bankDetails/
 * wallet).
 */
@Component
public class DriverResponseMapper {

    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverEmergencyContactRepository driverEmergencyContactRepository;
    private final DriverWalletRepository driverWalletRepository;

    public DriverResponseMapper(
            DriverDocumentRepository driverDocumentRepository,
            DriverEmergencyContactRepository driverEmergencyContactRepository,
            DriverWalletRepository driverWalletRepository) {
        this.driverDocumentRepository = driverDocumentRepository;
        this.driverEmergencyContactRepository = driverEmergencyContactRepository;
        this.driverWalletRepository = driverWalletRepository;
    }

    /** Full projection: documents + emergencyContacts + wallet, like {@code populate("wallet")}. */
    public DriverResponse toFullResponse(Driver driver) {
        List<DriverDocument> documents = driverDocumentRepository.findByDriver_Id(driver.getId());
        List<DriverEmergencyContact> contacts = driverEmergencyContactRepository.findByDriver_Id(driver.getId());
        DriverWallet wallet = driverWalletRepository.findByDriver_Id(driver.getId()).orElse(null);
        return toResponse(driver, documents, contacts, wallet);
    }

    /** Lighter projection without the wallet populate (matches plain {@code findOneDriver} calls). */
    public DriverResponse toResponseWithDocsAndContacts(Driver driver) {
        List<DriverDocument> documents = driverDocumentRepository.findByDriver_Id(driver.getId());
        List<DriverEmergencyContact> contacts = driverEmergencyContactRepository.findByDriver_Id(driver.getId());
        return toResponse(driver, documents, contacts, null);
    }

    /** Matches getDrivers' {@code select: {documents:0, bankDetails:0}} projection. */
    public DriverResponse toResponseExcludingDocsAndBank(Driver driver) {
        List<DriverEmergencyContact> contacts = driverEmergencyContactRepository.findByDriver_Id(driver.getId());
        DriverResponse response = toResponse(driver, List.of(), contacts, null);
        response.setDocuments(null);
        response.setBankDetails(null);
        return response;
    }

    public DriverResponse toResponse(Driver driver, List<DriverDocument> documents,
            List<DriverEmergencyContact> contacts, DriverWallet wallet) {
        DriverResponse response = new DriverResponse();
        response.setId(driver.getId() == null ? null : driver.getId().toString());
        response.setMobile(driver.getMobile());
        response.setEmail(driver.getEmail());
        response.setFirstName(driver.getFirstName());
        response.setLastName(driver.getLastName());
        response.setGender(driver.getGender());
        response.setState(driver.getState());
        response.setCity(driver.getCity());
        response.setProfileCompleted(driver.isProfileCompleted());
        response.setFcmToken(driver.getFcmToken());
        response.setSocketId(driver.getSocketId());
        response.setSocketOnline(driver.isSocketOnline());
        response.setStatus(driver.getStatus());
        response.setKycCompleted(driver.isKycCompleted());
        response.setKycStatus(driver.getKycStatus());
        response.setProfileImage(driver.getProfileImage());
        response.setUnverifiedDriver(driver.isUnverifiedDriver());
        response.setVehicleNumber(driver.getVehicleNumber());
        response.setChassisNumber(driver.getChassisNumber());
        response.setEngineNumber(driver.getEngineNumber());
        response.setFuelType(driver.getFuelType());
        response.setVehicleType(driver.getVehicleType() == null ? null : driver.getVehicleType().toString());
        response.setVehicleVariant(driver.getVehicleVariant() == null ? null : driver.getVehicleVariant().toString());
        response.setDriverStatus(driver.getDriverStatus());
        response.setLastOnlineAt(driver.getLastOnlineAt());
        if (driver.getCurrentLatitude() != null && driver.getCurrentLongitude() != null) {
            response.setCurrentLocation(new DriverResponse.GeoPointResponse(driver.getCurrentLongitude(), driver.getCurrentLatitude()));
        }
        response.setPreferredLocations(driver.getPreferredLocations());
        response.setOnRide(driver.isOnRide());
        response.setDelete(driver.isDeleted());
        response.setDeleteReason(driver.getDeleteReason());
        response.setTrips(driver.getRecentTripIds() == null ? List.of()
                : Arrays.stream(driver.getRecentTripIds()).map(UUID::toString).toList());
        response.setLastTripAt(driver.getLastTripAt());
        response.setReferralCode(driver.getReferralCode());
        response.setReferredBy(driver.getReferredBy() == null ? null : driver.getReferredBy().getId().toString());
        response.setBlocked(driver.isBlocked());
        response.setCreatedAt(driver.getCreatedAt());
        response.setUpdatedAt(driver.getUpdatedAt());
        String first = driver.getFirstName() == null ? "" : driver.getFirstName().trim();
        String last = driver.getLastName() == null ? "" : driver.getLastName().trim();
        response.setFullName((first + " " + last).trim());

        response.setDocuments(mapDocuments(documents));
        response.setEmergencyContacts(mapContacts(contacts));
        response.setBankDetails(mapBankDetails(driver));
        response.setWallet(mapWallet(wallet));
        return response;
    }

    private List<DriverResponse.DocumentResponse> mapDocuments(List<DriverDocument> documents) {
        List<DriverResponse.DocumentResponse> result = new ArrayList<>();
        if (documents == null) {
            return result;
        }
        for (DriverDocument doc : documents) {
            DriverResponse.DocumentResponse d = new DriverResponse.DocumentResponse();
            d.setKey(doc.getDocKey());
            d.setTitle(doc.getTitle());
            d.setStatus(doc.getStatus());
            d.setFiles(doc.getFiles());
            d.setFileStatus(doc.getFileStatus());
            result.add(d);
        }
        return result;
    }

    private List<DriverResponse.EmergencyContactResponse> mapContacts(List<DriverEmergencyContact> contacts) {
        List<DriverResponse.EmergencyContactResponse> result = new ArrayList<>();
        if (contacts == null) {
            return result;
        }
        for (DriverEmergencyContact contact : contacts) {
            DriverResponse.EmergencyContactResponse c = new DriverResponse.EmergencyContactResponse();
            c.setId(contact.getId().toString());
            c.setName(contact.getName());
            c.setRelation(contact.getRelation());
            c.setPhone(contact.getPhone());
            result.add(c);
        }
        return result;
    }

    private DriverResponse.BankDetailsResponse mapBankDetails(Driver driver) {
        DriverResponse.BankDetailsResponse bank = new DriverResponse.BankDetailsResponse();
        bank.setAccountNumber(driver.getBankAccountNumber());
        bank.setIfsc(driver.getBankIfsc());
        bank.setHolderName(driver.getBankHolderName());
        bank.setBankName(driver.getBankName());
        bank.setBranch(driver.getBankBranch());
        bank.setUpiId(driver.getBankUpiId());
        bank.setUpiQrImage(driver.getBankUpiQrImage());
        return bank;
    }

    private DriverResponse.WalletResponse mapWallet(DriverWallet wallet) {
        if (wallet == null) {
            return null;
        }
        DriverResponse.WalletResponse w = new DriverResponse.WalletResponse();
        w.setId(wallet.getId().toString());
        w.setDriverId(wallet.getDriver().getId().toString());
        w.setCurrentAmount(wallet.getCurrentAmount());
        w.setClaimAmount(wallet.getClaimAmount());
        w.setLastClaimDate(wallet.getLastClaimDate());
        w.setWalletStatus(wallet.getWalletStatus());
        w.setPendingAmount(wallet.getPendingAmount());
        w.setCreatedAt(wallet.getCreatedAt());
        w.setUpdatedAt(wallet.getUpdatedAt());
        return w;
    }
}
