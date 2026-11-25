package com.ridehail.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /update-address body, mirrors validations/uservalidation.js's updateAddressSchema:
 * {@code label}/{@code street1}/{@code type}/{@code coordinate} are required when
 * {@code action == "update"} and forbidden when {@code action == "delete"}; {@code street2}/
 * {@code landmark} are optional-but-non-empty-if-present on update and forbidden on delete;
 * {@code district} is always optional and empty-allowed regardless of action. Joi's conditional
 * {@code .when(...)} rules have no direct Jakarta Bean Validation equivalent, so they are
 * reproduced with class-level {@code @AssertTrue} checks below.
 */
@Getter
@Setter
public class UpdateAddressRequest {

    @NotBlank
    private String addressId;

    @NotBlank
    @Pattern(regexp = "update|delete", message = "action must be one of [update, delete]")
    private String action;

    /** Joi: {@code Joi.string().trim().allow("")} - optional, empty allowed, any action. */
    private String district;

    private String label;

    private String street1;

    private String street2;

    private String landmark;

    private String type;

    @Valid
    private CoordinateDto coordinate;

    private boolean isUpdate() {
        return "update".equals(action);
    }

    private boolean isDelete() {
        return "delete".equals(action);
    }

    @AssertTrue(message = "label is required when action is update, and not allowed when action is delete")
    public boolean isLabelValidForAction() {
        if (isDelete()) return label == null;
        if (isUpdate()) return label != null && !label.isBlank();
        return true;
    }

    @AssertTrue(message = "street1 is required when action is update, and not allowed when action is delete")
    public boolean isStreet1ValidForAction() {
        if (isDelete()) return street1 == null;
        if (isUpdate()) return street1 != null && !street1.isBlank();
        return true;
    }

    @AssertTrue(message = "street2 must not be empty when present, and is not allowed when action is delete")
    public boolean isStreet2ValidForAction() {
        if (isDelete()) return street2 == null;
        if (isUpdate()) return street2 == null || !street2.isBlank();
        return true;
    }

    @AssertTrue(message = "landmark must not be empty when present, and is not allowed when action is delete")
    public boolean isLandmarkValidForAction() {
        if (isDelete()) return landmark == null;
        if (isUpdate()) return landmark == null || !landmark.isBlank();
        return true;
    }

    @AssertTrue(message = "type must be one of [home, work, other] when action is update, and not allowed when action is delete")
    public boolean isTypeValidForAction() {
        if (isDelete()) return type == null;
        if (isUpdate()) return type != null && type.matches("home|work|other");
        return true;
    }

    @AssertTrue(message = "coordinate is required when action is update, and not allowed when action is delete")
    public boolean isCoordinateValidForAction() {
        if (isDelete()) return coordinate == null;
        if (isUpdate()) return coordinate != null && coordinate.getLat() != null && coordinate.getLng() != null;
        return true;
    }
}
