package com.ridehail.userservice.dto;

/**
 * POST /update-profile. No validation is applied in the original (routes/user.routes.js mounts
 * this route without {@code validateRequest}, even though uservalidation.js defines an unused
 * updateProfileSchema) - every field here is optional, matching that.
 */
public class UpdateProfileRequest {

    private String firstname;
    private String lastname;
    private String gender;
    private String email;
    private String mobile;
    private Boolean isBlocked;
    private String userid;

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Boolean getIsBlocked() {
        return isBlocked;
    }

    public void setIsBlocked(Boolean isBlocked) {
        this.isBlocked = isBlocked;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }
}
