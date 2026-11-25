package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/** Equivalent of validations/driver.validation.js's kycverifyDriverSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KycVerifyRequest {

    @NotBlank
    private String _id;

    @NotBlank(message = "Document type is required")
    private String idtype;

    @NotBlank(message = "Verify status is required")
    private String verifyStatus;

    @NotBlank(message = "Email is required")
    private String email;

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getIdtype() {
        return idtype;
    }

    public void setIdtype(String idtype) {
        this.idtype = idtype;
    }

    public String getVerifyStatus() {
        return verifyStatus;
    }

    public void setVerifyStatus(String verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
