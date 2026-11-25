package com.ridehail.driverservice.dto;

import org.springframework.web.multipart.MultipartFile;

/** Equivalent of validations/driver.validation.js's bankDetailsSchema. */
public class UpdateBankDetailsForm {

    private String holderName;
    private String accountNumber;
    private String confirmAccountNumber;
    private String ifsc;
    private String bankName;
    private String branch;
    private String upiId;
    private MultipartFile upiQrImage;
    private MultipartFile upiQr;

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getConfirmAccountNumber() {
        return confirmAccountNumber;
    }

    public void setConfirmAccountNumber(String confirmAccountNumber) {
        this.confirmAccountNumber = confirmAccountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
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

    public MultipartFile getUpiQrImage() {
        return upiQrImage;
    }

    public void setUpiQrImage(MultipartFile upiQrImage) {
        this.upiQrImage = upiQrImage;
    }

    public MultipartFile getUpiQr() {
        return upiQr;
    }

    public void setUpiQr(MultipartFile upiQr) {
        this.upiQr = upiQr;
    }
}
