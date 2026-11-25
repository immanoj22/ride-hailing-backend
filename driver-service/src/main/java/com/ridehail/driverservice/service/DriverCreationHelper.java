package com.ridehail.driverservice.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverDocument;
import com.ridehail.driverservice.entity.DriverWallet;
import com.ridehail.driverservice.repository.DriverDocumentRepository;
import com.ridehail.driverservice.repository.DriverRepository;
import com.ridehail.driverservice.repository.DriverWalletRepository;

/**
 * Equivalent of service/driver.services.js's {@code driverSave} (creates the driver + its wallet)
 * plus the 6 default {@code documents} sub-rows driver.schema.js seeds via its schema default -
 * modelled here as {@link DriverDocument} child rows (see {@link Driver}'s class notes).
 */
@Component
public class DriverCreationHelper {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REFERRAL_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private record DocSeed(String key, String title) {
    }

    private static final List<DocSeed> DEFAULT_DOCUMENTS = List.of(
            new DocSeed("aadhaar", "Aadhaar/PAN/Passport (Local address proof)"),
            new DocSeed("drivinglicense", "Driving License"),
            new DocSeed("RC", "Registration Certificate - RC"),
            new DocSeed("insurance", "Vehicle Insurance"),
            new DocSeed("vehicle_images", "Vehicle Images"),
            new DocSeed("permit", "Vehicle Permit"));

    private final DriverRepository driverRepository;
    private final DriverWalletRepository driverWalletRepository;
    private final DriverDocumentRepository driverDocumentRepository;

    public DriverCreationHelper(
            DriverRepository driverRepository,
            DriverWalletRepository driverWalletRepository,
            DriverDocumentRepository driverDocumentRepository) {
        this.driverRepository = driverRepository;
        this.driverWalletRepository = driverWalletRepository;
        this.driverDocumentRepository = driverDocumentRepository;
    }

    @Transactional
    public Driver save(Driver driver) {
        Driver saved = driverRepository.save(driver);
        driverWalletRepository.save(DriverWallet.builder().driver(saved).build());
        for (DocSeed seed : DEFAULT_DOCUMENTS) {
            driverDocumentRepository.save(DriverDocument.builder()
                    .driver(saved)
                    .docKey(seed.key())
                    .title(seed.title())
                    .build());
        }
        return saved;
    }

    /** Equivalent of service/driver.services.js's getRefCodeWithCheck(). */
    public String generateUniqueReferralCode() {
        String code;
        do {
            code = generateReferralCode(8);
        } while (driverRepository.findByReferralCode(code).isPresent());
        return code;
    }

    private String generateReferralCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(REFERRAL_CODE_CHARS.charAt(RANDOM.nextInt(REFERRAL_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
