package com.ridehail.userservice.dto;

import lombok.Data;

/**
 * Request body for {@code POST /user-apis/get-users-ride-report}. Mirrors the untyped
 * {@code {searchText, page, limit}} shape read off {@code req.body} in
 * user.controller.js#get_users_ride_report - no Joi schema in the original, so no validation
 * constraints are added here.
 */
@Data
public class GetUsersRideReportRequest {

    private String searchText;

    private Integer page;

    private Integer limit;
}
