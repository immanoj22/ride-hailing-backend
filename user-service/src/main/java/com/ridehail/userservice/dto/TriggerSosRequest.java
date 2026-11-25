package com.ridehail.userservice.dto;

/**
 * Body of POST /trigger-sos (controller/user.controller.js#trigger_sos). No Joi schema exists for
 * this route in the original either - {@code rideId}/{@code location.lat}/{@code location.lng}
 * required-ness is enforced with hand-written checks (and exact error messages) in
 * RideProxyService, not bean validation, so the original's field-by-field short-circuit order and
 * wording is preserved exactly.
 */
public class TriggerSosRequest {

    private String rideId;
    private SosLocation location;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public SosLocation getLocation() {
        return location;
    }

    public void setLocation(SosLocation location) {
        this.location = location;
    }

    /** Field names {@code lat}/{@code lng} match the legacy {@code req.body.location.lat/.lng} wire shape exactly. */
    public static class SosLocation {

        private Double lat;
        private Double lng;

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }

        public Double getLng() {
            return lng;
        }

        public void setLng(Double lng) {
            this.lng = lng;
        }
    }
}
