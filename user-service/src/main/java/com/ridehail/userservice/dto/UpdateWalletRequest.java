package com.ridehail.userservice.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /user-apis/update-wallet. The original route has no Joi schema attached, so
 * {@code amount} is validated/coerced by hand in the service exactly like
 * user.controller.js#update_wallet does (it accepts either a JSON number or a numeric string).
 */
@Getter
@Setter
public class UpdateWalletRequest {

    private String userId;

    private Object amount;
}
