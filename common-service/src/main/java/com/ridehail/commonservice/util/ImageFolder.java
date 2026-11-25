package com.ridehail.commonservice.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mirrors the legacy {@code folders} map (helpers/constants.js) used to validate S3 upload targets. */
public final class ImageFolder {

    private static final Map<String, String> FOLDERS = new LinkedHashMap<>();

    static {
        FOLDERS.put("public_user", "public/user");
        FOLDERS.put("public_adminimages", "public/adminimages");
        FOLDERS.put("public_pricecal", "public/pricecalculation");
        FOLDERS.put("public_vehicle", "public/vehicle");
        FOLDERS.put("public_driver", "public/driver");
        FOLDERS.put("public_coupon", "public/coupon");
        FOLDERS.put("public_pushnotification", "public/pushnotification");
        FOLDERS.put("public_chat", "public/chat");
        FOLDERS.put("public_ride", "public/ride");
        FOLDERS.put("public_cms", "public/cms");
        FOLDERS.put("public_vehicle_variant", "public/vehiclevariant");
        FOLDERS.put("public_vehicle_type", "public/vehicletype");
        FOLDERS.put("public_support_ticket", "public/supportticket");
    }

    private ImageFolder() {
    }

    public static Map<String, String> all() {
        return FOLDERS;
    }

    public static boolean isValidKey(String key) {
        return FOLDERS.containsKey(key);
    }

    public static String pathFor(String key) {
        return FOLDERS.get(key);
    }
}
