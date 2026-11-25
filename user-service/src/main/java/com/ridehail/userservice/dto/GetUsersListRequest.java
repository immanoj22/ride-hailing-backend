package com.ridehail.userservice.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Request body for {@code POST /user-apis/get-users-list} (admin listing/search). Mirrors the ad
 * hoc {@code {userId, phoneNumbers, options, select, search, isBlocked}} shape read directly off
 * {@code req.body} in user.controller.js#get_users_list - the original route has no Joi schema, so
 * no Bean Validation constraints are added here either.
 */
@Data
public class GetUsersListRequest {

    private String userId;

    private List<String> phoneNumbers;

    private Options options;

    /** Mongoose-style projection map, e.g. {"firstname": 1, "email": 1}. */
    private Map<String, Object> select;

    private String search;

    private Boolean isBlocked;

    @Data
    public static class Options {
        private String fromDate;
        private String toDate;
        private Integer limit;
        private Integer skip;
        /** Mongoose-style sort map, e.g. {"createdAt": -1}. */
        private Map<String, Integer> sort;
    }
}
