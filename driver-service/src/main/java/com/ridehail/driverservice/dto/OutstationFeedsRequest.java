package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OutstationFeedsRequest {

    private Boolean seeAll = false;
    private String city;
    private Integer page = 1;
    private Integer limit = 10;
    private String tripType;

    public Boolean getSeeAll() {
        return seeAll;
    }

    public void setSeeAll(Boolean seeAll) {
        this.seeAll = seeAll;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getTripType() {
        return tripType;
    }

    public void setTripType(String tripType) {
        this.tripType = tripType;
    }
}
