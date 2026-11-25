package com.ridehail.userservice.util;

/** Mirrors helpers/user.constants.js's USER_ACTIVITY action-name map. */
public final class UserActivityAction {

    public static final String REGISTER = "register";

    public static final String LOGIN_WITH_SOCIAL = "loginWithSocialLogin";
    public static final String LOGIN_WITH_GMAIL = "loginWithGmail";
    public static final String LOGIN_WITH_MOBILE = "loginWithMobile";
    public static final String LOGIN_WITH_MOBILE_OTP = "loginWithMobileOtp";
    public static final String LOGIN_USER = "userlogin";

    public static final String UPDATE_USER = "updateUser";
    public static final String UPDATE_FCM = "updateFcm";
    public static final String UPDATE_LOCATION = "updateLocation";
    public static final String UPDATE_PREFERENCES = "updatePreferences";

    public static final String ADD_ADDRESS = "addAddress";
    public static final String EDIT_ADDRESS = "editAddress";
    public static final String DELETE_ADDRESS = "deleteAddress";

    public static final String LOGOUT_USER = "logoutUser";
    public static final String REMOVE_USER_ACCOUNT = "removeUserAccount";

    private UserActivityAction() {
    }
}
