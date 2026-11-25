package com.ridehail.commonservice.util;

/** Mirrors GOOGLE_API_URLS (helpers/constants.js). */
public final class GoogleApiUrls {

    public static final String GOOGLE_MAP_URL = "https://maps.googleapis.com/maps/api/";
    public static final String GET_ADDRESS = GOOGLE_MAP_URL + "geocode/json?address=";
    public static final String DIRECTIONS = GOOGLE_MAP_URL + "directions/json";

    private GoogleApiUrls() {
    }
}
