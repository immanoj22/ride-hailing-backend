package com.ridehail.socketservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /socket-apis/saveChat (multipart form fields), mirroring the destructuring in
 * controller/socket.controller.js#saveChatMessage: {@code { rideId, senderId, from, msg }}.
 * The route mounts {@code saveChatSchema} commented out, so only the fields the handler actually
 * relies on are constrained here.
 */
@Getter
@Setter
public class SaveChatRequest {

    @NotBlank
    private String rideId;

    @NotBlank
    private String senderId;

    @NotBlank
    private String from;

    private String msg;
}
