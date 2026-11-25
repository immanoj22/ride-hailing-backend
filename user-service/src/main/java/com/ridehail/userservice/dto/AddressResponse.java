package com.ridehail.userservice.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ridehail.userservice.entity.Address;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wire shape of a Mongoose "address" document (modals/address.schema.js), returned as-is by
 * add_address/update_address. Field names/keys mirror the original schema exactly, including the
 * quirky lowercase {@code userid} key and the Mongo {@code _id} key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {

    @JsonProperty("_id")
    private UUID id;

    private String label;

    private String street1;

    private String street2;

    private String district;

    private String landmark;

    private String type;

    /**
     * Original Mongoose field was a String ("0"/"1"); the Foundation-step migration normalized it
     * to a real boolean column (see V1__init.sql notes) - surfaced here as a JSON boolean under
     * the same "delete" key.
     */
    @JsonProperty("delete")
    private Boolean delete;

    private UUID userid;

    private GeoPoint coordinate;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public static AddressResponse from(Address address) {
        GeoPoint point = null;
        if (address.getCoordinateLat() != null && address.getCoordinateLng() != null) {
            point = new GeoPoint("Point", List.of(address.getCoordinateLng(), address.getCoordinateLat()));
        }
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getStreet1(),
                address.getStreet2(),
                address.getDistrict(),
                address.getLandmark(),
                address.getType(),
                address.getDeleted(),
                address.getUserId(),
                point,
                address.getCreatedAt(),
                address.getUpdatedAt());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GeoPoint {
        private String type;
        private List<Double> coordinates;
    }
}
