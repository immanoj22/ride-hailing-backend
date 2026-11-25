package com.ridehail.socketservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.security.InternalServiceClient;

/**
 * Bridges the {@code chat:send} / {@code chat:user:send} socket handlers to the persistence layer,
 * mirroring config/socketListener.js:
 * <ul>
 *   <li>{@code chat:user:send} -> the REST chat service's {@code saveChatMessage}
 *       (POST {@code /socket-apis/saveChat}); that controller also performs the ride lookup and
 *       emits {@code chat:user:receive}.</li>
 *   <li>{@code chat:send} -> helperdesk-service {@code /add-message} (utills/socket.axios.js
 *       {@code saveMessages}).</li>
 * </ul>
 * The REST controller/service themselves are owned by another migration bucket; this only calls
 * their endpoints, exactly like the Node code called them in-process / via axios.
 */
@Service
public class SocketChatBridgeService {

    private static final Logger log = LoggerFactory.getLogger(SocketChatBridgeService.class);

    private final InternalServiceClient internalServiceClient;
    private final String socketUrl;
    private final String helperdeskUrl;

    public SocketChatBridgeService(
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.socket-url}") String socketUrl,
            @Value("${ridehail.services.helperdesk-url}") String helperdeskUrl) {
        this.internalServiceClient = internalServiceClient;
        this.socketUrl = socketUrl;
        this.helperdeskUrl = helperdeskUrl;
    }

    /** chat:user:send - Node reqObj.body was { rideId, senderId, from, msg }. */
    public JsonNode saveRideChat(String rideId, String senderId, String from, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rideId", rideId);
        body.put("senderId", senderId);
        body.put("from", from);
        body.put("msg", msg);
        try {
            return internalServiceClient.post(socketUrl + "/saveChat", body);
        } catch (Exception e) {
            log.error("saveRideChat failed", e);
            return null;
        }
    }

    /** chat:send - Node saveMessages() PUT body was
     *  { supportTicketId, userId, message, sender, file }. */
    public JsonNode saveSupportMessage(String supportTicketId, String userId, String message, String sender, String file) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supportTicketId", supportTicketId);
        body.put("userId", userId);
        body.put("message", message);
        body.put("sender", sender);
        body.put("file", file);
        try {
            // NOTE: legacy used HTTP PUT; InternalServiceClient only exposes GET/POST, so this is
            // a POST. helperdesk-service is not yet migrated - endpoint/method to be reconciled
            // when it is.
            return internalServiceClient.post(helperdeskUrl + "/add-message", body);
        } catch (Exception e) {
            log.error("saveSupportMessage failed", e);
            return null;
        }
    }
}
