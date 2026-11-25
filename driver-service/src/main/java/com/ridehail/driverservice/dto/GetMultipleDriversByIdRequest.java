package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code driverIds} may be an array or a comma-separated string in the original. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMultipleDriversByIdRequest {

    private Object driverIds;

    public Object getDriverIds() {
        return driverIds;
    }

    public void setDriverIds(Object driverIds) {
        this.driverIds = driverIds;
    }
}
