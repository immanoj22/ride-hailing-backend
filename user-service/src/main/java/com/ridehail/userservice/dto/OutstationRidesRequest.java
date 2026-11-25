package com.ridehail.userservice.dto;

/**
 * Body of POST /outstation/rides (controller/user.controller.js#get_outstation_rides). The
 * original has no Joi schema for this route - {@code page}/{@code limit} simply default via JS
 * destructuring ({@code const { status, page = 1, limit = 10 } = req.body}) when the field is
 * missing/undefined; any other value (including 0 or negative) is passed through untouched, so no
 * bean-validation constraints are added here to avoid inventing stricter rules than the original.
 */
public class OutstationRidesRequest {

    private String status;
    private Integer page;
    private Integer limit;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
