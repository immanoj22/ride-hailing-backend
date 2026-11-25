package com.ridehail.userservice.dto;

import lombok.Data;

/**
 * Request body for {@code POST /user-apis/get-user-notifications}. Mirrors the untyped
 * {@code {action, mobile}} shape read off {@code req.body} in
 * user.controller.js#get_user_notification - no Joi schema in the original. {@code mobile} is
 * left as {@code Object} because the legacy handler accepts either a single mobile number
 * (String) or an array of numbers (List) for {@code action == "individual"}.
 */
@Data
public class GetUserNotificationRequest {

    private String action;

    private Object mobile;
}
