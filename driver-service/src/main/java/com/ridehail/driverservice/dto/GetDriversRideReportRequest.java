package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GetDriversRideReportRequest {

    private String searchText;
    private Boolean kycCompleted;
    private String kycStatus;
    private String driverStatus;
    private String genderFilter;
    private Boolean blockFilter;
    private Boolean deleted;
    private Integer page = 1;
    private Integer limit = 10;
    private Integer sort;

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public Boolean getKycCompleted() {
        return kycCompleted;
    }

    public void setKycCompleted(Boolean kycCompleted) {
        this.kycCompleted = kycCompleted;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getDriverStatus() {
        return driverStatus;
    }

    public void setDriverStatus(String driverStatus) {
        this.driverStatus = driverStatus;
    }

    public String getGenderFilter() {
        return genderFilter;
    }

    public void setGenderFilter(String genderFilter) {
        this.genderFilter = genderFilter;
    }

    public Boolean getBlockFilter() {
        return blockFilter;
    }

    public void setBlockFilter(Boolean blockFilter) {
        this.blockFilter = blockFilter;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
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

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
