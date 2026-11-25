package com.ridehail.notificationservice.dto;

import java.util.List;

/**
 * POST /send-push-notification. Mirrors controllers/notification.controller.js's pushNotification
 * body: {@code {title, message, whom, coupon, mobile, imageUrl}}. {@code whom} is one of
 * user/driver/selectedUser/selectedDriver. The original also accepted a multipart {@code imageFile}
 * upload - this port takes a pre-uploaded {@code imageUrl} instead (the admin panel can upload via
 * common-service's /image-upload first).
 */
public class PushNotificationRequest {

    private String title;
    private String message;
    private String whom;
    private String coupon = "";
    private List<String> mobile;
    private String imageUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getWhom() {
        return whom;
    }

    public void setWhom(String whom) {
        this.whom = whom;
    }

    public String getCoupon() {
        return coupon;
    }

    public void setCoupon(String coupon) {
        this.coupon = coupon;
    }

    public List<String> getMobile() {
        return mobile;
    }

    public void setMobile(List<String> mobile) {
        this.mobile = mobile;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
