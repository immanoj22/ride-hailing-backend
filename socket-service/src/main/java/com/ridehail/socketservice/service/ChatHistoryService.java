package com.ridehail.socketservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.realtime.SocketRelayPublisher;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.socketservice.dto.SaveChatRequest;
import com.ridehail.socketservice.entity.ChatMessage;
import com.ridehail.socketservice.repository.ChatMessageRepository;

/**
 * Port of services/socket.services.js + the chat-history half of controller/socket.controller.js
 * (saveChatMessage, getRideMessage, getChatMessage, getMessageFromUser, getMessageByRideId,
 * getChatUsingTicketId). Response maps deliberately reproduce the exact legacy JSON field names
 * (mongo {@code _id}, {@code driverId}/{@code userId} replaced in place by the fetched object,
 * {@code adminData}, {@code rideData}, {@code userData}).
 */
@Service
public class ChatHistoryService {

    /** helper/socket.constants.js CHAT_KEY.CHAT_USER_RECEIVE. */
    private static final String CHAT_USER_RECEIVE = "chat:user:receive";
    /** helper/socket.constants.js CHAT_KEY.CHAT_RECEIVE. */
    private static final String CHAT_RECEIVE = "chat:receive";
    /** helper/socket.constants.js IMAGE_FOLDERS.public_chat. */
    private static final String PUBLIC_CHAT_FOLDER = "public_chat";

    private final ChatMessageRepository chatMessageRepository;
    private final InternalServiceClient internalServiceClient;
    private final ChatImageUploadClient chatImageUploadClient;
    private final SocketRelayPublisher socketRelayPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String userServiceUrl;
    private final String driverServiceUrl;
    private final String adminServiceUrl;
    private final String rideServiceUrl;

    public ChatHistoryService(
            ChatMessageRepository chatMessageRepository,
            InternalServiceClient internalServiceClient,
            ChatImageUploadClient chatImageUploadClient,
            SocketRelayPublisher socketRelayPublisher,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl) {
        this.chatMessageRepository = chatMessageRepository;
        this.internalServiceClient = internalServiceClient;
        this.chatImageUploadClient = chatImageUploadClient;
        this.socketRelayPublisher = socketRelayPublisher;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
        this.rideServiceUrl = rideServiceUrl;
    }

    // ------------------------------------------------------------------
    // saveChatMessage
    // ------------------------------------------------------------------

    @Transactional
    public Map<String, Object> saveChatMessage(SaveChatRequest request, MultipartFile file) {
        List<String> fileArr = new ArrayList<>();
        if (file != null && !file.isEmpty()) {
            String subFolder = request.getFrom() + "/" + request.getRideId();
            String fileUrl = chatImageUploadClient.upload(file, subFolder, PUBLIC_CHAT_FOLDER);
            if (fileUrl != null) {
                fileArr.add(fileUrl);
            }
        }

        // add_Message writes only { rideId, senderId, from, msg, file } (Mongoose drops the rest).
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .rideId(parseUuid(request.getRideId()))
                .senderId(parseUuid(request.getSenderId()))
                .from(request.getFrom())
                .msg(request.getMsg() != null ? request.getMsg() : "")
                .file(fileArr)
                .seen(false)
                .build());

        Map<String, Object> addMessage = toFullMap(saved);

        JsonNode ride = getRideById(request.getRideId());
        if (ride != null && ride.path("status").asBoolean(false)) {
            JsonNode rideData = ride.path("data");
            String userId = rideData.path("userId").asText(null);
            String driverId = rideData.path("driverId").asText(null);
            Map<String, Object> targeted = new LinkedHashMap<>();
            targeted.put("success", true);
            targeted.put("data", addMessage);
            if (userId != null) {
                socketRelayPublisher.toUser(userId, CHAT_USER_RECEIVE, targeted);
            }
            if (driverId != null) {
                socketRelayPublisher.toDriver(driverId, CHAT_USER_RECEIVE, targeted);
            }
        }

        Map<String, Object> global = new LinkedHashMap<>();
        global.put("success", true);
        global.put("message", "Message added successfully");
        global.put("data", addMessage);
        socketRelayPublisher.broadcast(CHAT_USER_RECEIVE, global);

        return addMessage;
    }

    // ------------------------------------------------------------------
    // getRideMessage  -  GET /get-ride-chat/{rideId}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRideMessage(String rideId) {
        List<ChatMessage> messages = chatMessageRepository.findLatestMessagePerDriverForRide(parseUuid(rideId));
        if (messages.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No chat is there for this ride");
        }

        JsonNode rideRes = getRideById(rideId);
        JsonNode rideData = rideRes != null ? rideRes.path("data") : null;
        if (rideRes == null || !rideRes.path("status").asBoolean(false) || rideData == null || rideData.isMissingNode()
                || rideData.isNull()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No ride details are available");
        }
        Object rideDataObj = objectMapper.convertValue(rideData, Object.class);

        List<String> driverIds = messages.stream()
                .map(ChatMessage::getDriverId).filter(java.util.Objects::nonNull)
                .map(UUID::toString).distinct().collect(Collectors.toList());

        Map<String, JsonNode> driverMap = new LinkedHashMap<>();
        if (!driverIds.isEmpty()) {
            JsonNode driverRes = internalServiceClient.get(
                    driverServiceUrl + "/profile?driverIds=" + toJsonArray(driverIds));
            JsonNode driverDetails = driverRes.path("data");
            if (!driverDetails.isArray() || driverDetails.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No driver details are available");
            }
            for (JsonNode d : driverDetails) {
                driverMap.put(d.path("_id").asText(), d);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", m.getDriverId() != null ? m.getDriverId().toString() : null);
            row.put("msg", m.getMsg());
            row.put("from", m.getFrom());
            JsonNode driverData = m.getDriverId() != null ? driverMap.get(m.getDriverId().toString()) : null;
            row.put("driverId", driverData != null ? objectMapper.convertValue(driverData, Object.class) : null);
            row.put("createdAt", m.getCreatedAt());
            row.put("rideData", rideDataObj);
            result.add(row);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // getChatMessage  -  GET /get-driver-admin-chat/{rideId}/{driverId}
    //
    // Legacy calls get_chat_message({ driverId, rideId }) against a helper that only ever filters
    // by ticketId - which is undefined here, so in the legacy app this always resolves to an empty
    // result and returns "No chat is there for this ride". Ported literally: lookup by ticketId
    // null (always empty). The req.driver / req.admin enrichment branches are kept for parity but
    // are effectively unreachable, exactly as in the original.
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChatMessage(String rideId, String driverId) {
        if (driverId == null || driverId.isBlank() || rideId == null || rideId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "DriverId or RideId is missing");
        }

        List<ChatMessage> messages = chatMessageRepository.findByTicketIdOrderByCreatedAtAsc(null);
        if (messages.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No chat is there for this ride");
        }

        AuthContext auth = AuthContext.current();
        AuthUserType type = auth != null ? auth.authUserType() : null;
        JsonNode principal = auth != null ? auth.principal() : null;
        List<Map<String, Object>> finalMessages = new ArrayList<>();

        if (type == AuthUserType.DRIVER) {
            JsonNode adminRes = internalServiceClient.get(adminServiceUrl + "/get-admin");
            JsonNode adminDetails = adminRes.path("data");
            if (!adminDetails.isArray() || adminDetails.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No admin details are available");
            }
            Object adminObj = objectMapper.convertValue(adminDetails, Object.class);
            Map<String, Object> driverObj = pick(principal, "_id", "firstName", "lastName", "profileImage", "mobile");
            for (ChatMessage m : messages) {
                Map<String, Object> row = selectMsgUser(m);
                row.put("adminData", adminObj);
                row.put("driverId", driverObj);
                finalMessages.add(row);
            }
        } else if (type == AuthUserType.ADMIN) {
            Map<String, Object> adminObj = pick(principal, "_id", "fullname", "email", "mobileno", "profile");
            List<String> driverIds = messages.stream().map(ChatMessage::getDriverId)
                    .filter(java.util.Objects::nonNull).map(UUID::toString).distinct().collect(Collectors.toList());
            JsonNode driverRes = internalServiceClient.get(
                    driverServiceUrl + "/profile?driverIds=" + toJsonArray(driverIds));
            JsonNode driverDetails = driverRes.path("data");
            if (!driverDetails.isArray() || driverDetails.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No driver details are available");
            }
            Map<String, JsonNode> driverMap = new LinkedHashMap<>();
            for (JsonNode d : driverDetails) {
                driverMap.put(d.path("_id").asText(), d);
            }
            for (ChatMessage m : messages) {
                Map<String, Object> row = selectMsgUser(m);
                row.put("adminData", adminObj);
                JsonNode dd = m.getDriverId() != null ? driverMap.get(m.getDriverId().toString()) : null;
                row.put("driverId", dd != null ? objectMapper.convertValue(dd, Object.class) : null);
                finalMessages.add(row);
            }
        }

        return finalMessages;
    }

    // ------------------------------------------------------------------
    // getMessageFromUser  -  GET /get-chat?userId&rideId&driverId
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessageFromUser(String userId, String rideId, String driverId) {
        if (isBlank(rideId) && isBlank(userId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "rideId is required");
        }

        List<ChatMessage> getMessages = getChatFromUser(userId, rideId, driverId);
        if (getMessages.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No chat is there for this ride");
        }

        AuthContext auth = AuthContext.current();
        AuthUserType type = auth != null ? auth.authUserType() : null;
        JsonNode principal = auth != null ? auth.principal() : null;
        List<Map<String, Object>> finalMessages = new ArrayList<>();

        if (type == AuthUserType.DRIVER && !isBlank(userId)) {
            Map<String, Object> driverObj = pick(principal, "_id", "firstName", "lastName", "profileImage", "mobile");
            JsonNode userRes = internalServiceClient.get(userServiceUrl + "/profile/" + userId);
            JsonNode userDetails = userRes.path("data");
            if (userDetails.isMissingNode() || userDetails.isNull()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No user details are available");
            }
            Map<String, Object> userObj = pick(userDetails.path("user"), "_id", "firstname", "lastname", "mobile", "profile");
            for (ChatMessage m : getMessages) {
                Map<String, Object> row = selectMsgUserDriverRide(m);
                row.put("driverId", driverObj);
                row.put("userId", userObj);
                finalMessages.add(row);
            }
        }

        if (type == AuthUserType.USER && !isBlank(driverId)) {
            Map<String, Object> userObj = pick(principal != null ? principal.path("user") : null,
                    "_id", "firstname", "lastname", "mobile", "profile");
            List<String> driverIds = getMessages.stream().map(ChatMessage::getDriverId)
                    .filter(java.util.Objects::nonNull).map(UUID::toString).distinct().collect(Collectors.toList());
            JsonNode driverRes = internalServiceClient.get(
                    driverServiceUrl + "/profile?driverIds=" + toJsonArray(driverIds));
            JsonNode driverDetails = driverRes.path("data");
            if (!driverDetails.isArray() || driverDetails.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No driver details are available");
            }
            Map<String, JsonNode> driverMap = new LinkedHashMap<>();
            for (JsonNode d : driverDetails) {
                driverMap.put(d.path("_id").asText(), d);
            }
            for (ChatMessage m : getMessages) {
                Map<String, Object> row = selectMsgUserDriverRide(m);
                row.put("userId", userObj);
                JsonNode dd = m.getDriverId() != null ? driverMap.get(m.getDriverId().toString()) : null;
                row.put("driverId", dd != null ? objectMapper.convertValue(dd, Object.class) : null);
                finalMessages.add(row);
            }
        }

        if (type == AuthUserType.USER && isBlank(driverId)) {
            Map<String, Object> userObj = pick(principal != null ? principal.path("user") : null,
                    "_id", "firstname", "lastname", "mobile", "profile");
            JsonNode adminRes = internalServiceClient.get(adminServiceUrl + "/get-admin");
            JsonNode adminDetails = adminRes.path("data");
            if (adminDetails.isMissingNode() || adminDetails.isNull()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No admin details are available");
            }
            Object adminObj = objectMapper.convertValue(adminDetails, Object.class);
            for (ChatMessage m : getMessages) {
                Map<String, Object> row = selectMsgUserDriverRide(m);
                row.put("userId", userObj);
                row.put("adminData", adminObj);
                finalMessages.add(row);
            }
        }

        if (type == AuthUserType.ADMIN && !isBlank(userId)) {
            Map<String, Object> adminObj = pick(principal, "_id", "fullname", "email", "mobileno", "profile");
            JsonNode userRes = internalServiceClient.get(userServiceUrl + "/profile/" + userId);
            JsonNode userDetails = userRes.path("data");
            if (userDetails.isMissingNode() || userDetails.isNull()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No user details are available");
            }
            Map<String, Object> userObj = pick(userDetails.path("user"), "_id", "firstname", "lastname", "mobile", "profile");
            for (ChatMessage m : getMessages) {
                Map<String, Object> row = selectMsgUserDriverRide(m);
                row.put("userId", userObj);
                row.put("adminData", adminObj);
                finalMessages.add(row);
            }
        }

        return finalMessages;
    }

    // ------------------------------------------------------------------
    // getMessageByRideId  -  GET /get-message-by-rideid/{rideId}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessageByRideId(String rideId) {
        JsonNode rideRes = getRideById(rideId);
        JsonNode rideData = rideRes != null ? rideRes.path("data") : null;
        if (rideRes == null || !rideRes.path("status").asBoolean(false) || rideData == null || rideData.isMissingNode()
                || rideData.isNull()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No ride details are available");
        }

        List<ChatMessage> messages = chatMessageRepository.findByRideIdOrderByCreatedAtAsc(parseUuid(rideId));
        if (messages.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No chat is there for this ride");
        }
        return messages.stream().map(this::toFullMap).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // getChatUsingTicketId  -  GET /get-chat-by-ticketid/{ticketId}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChatUsingTicketId(String ticketId) {
        if (isBlank(ticketId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "TicketId is missing");
        }

        List<ChatMessage> messages = chatMessageRepository.findByTicketIdOrderByCreatedAtAsc(parseUuid(ticketId));
        if (messages.isEmpty()) {
            Map<String, Object> emit = new LinkedHashMap<>();
            emit.put("success", false);
            emit.put("message", "No chat is there for this ticket");
            emit.put("data", new ArrayList<>());
            socketRelayPublisher.broadcast(CHAT_RECEIVE, emit);
            throw new ApiException(StatusCode.BAD_REQUEST, "No chat is there for this ticket");
        }

        List<Map<String, Object>> finalMessages = new ArrayList<>();
        for (ChatMessage m : messages) {
            Object userData = null;
            try {
                if ("user".equals(m.getFrom()) && m.getUserId() != null) {
                    JsonNode res = internalServiceClient.get(userServiceUrl + "/profile/" + m.getUserId());
                    JsonNode u = res.path("data").path("user");
                    userData = u.isMissingNode() || u.isNull() ? null : objectMapper.convertValue(u, Object.class);
                } else if ("driver".equals(m.getFrom()) && m.getUserId() != null) {
                    JsonNode res = internalServiceClient.get(driverServiceUrl + "/profile/" + m.getUserId());
                    JsonNode d = res.path("data");
                    userData = d.isMissingNode() || d.isNull() ? null : objectMapper.convertValue(d, Object.class);
                } else if ("admin".equals(m.getFrom())) {
                    JsonNode res = internalServiceClient.get(adminServiceUrl + "/get-admin");
                    JsonNode a = res.path("data");
                    userData = a.isMissingNode() || a.isNull() ? null : objectMapper.convertValue(a, Object.class);
                }
            } catch (Exception ignored) {
                // matches the per-message try/catch in the original
            }
            Map<String, Object> row = selectMsgUser(m);
            row.put("userData", userData);
            finalMessages.add(row);
        }

        Map<String, Object> emit = new LinkedHashMap<>();
        emit.put("success", true);
        emit.put("message", "Chats are available");
        emit.put("data", finalMessages);
        socketRelayPublisher.broadcast(CHAT_RECEIVE, emit);

        return finalMessages;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** get_chat_from_user: neither -> direct admin/user chat; both -> ride chat; otherwise Chat.find({}). */
    private List<ChatMessage> getChatFromUser(String userId, String rideId, String driverId) {
        boolean hasRide = !isBlank(rideId);
        boolean hasDriver = !isBlank(driverId);
        if (!hasRide && !hasDriver) {
            return chatMessageRepository.findByUserIdAndRideIdIsNullAndFromInOrderByCreatedAtAsc(
                    parseUuid(userId), List.of("admin", "user"));
        }
        if (hasRide && hasDriver) {
            return chatMessageRepository.findByUserIdAndDriverIdAndRideIdAndFromInOrderByCreatedAtAsc(
                    parseUuid(userId), parseUuid(driverId), parseUuid(rideId), List.of("user", "driver"));
        }
        // legacy: query stays {} -> Chat.find({}) returns everything, oldest first
        return chatMessageRepository.findAll(
                org.springframework.data.domain.Sort.by("createdAt").ascending());
    }

    private JsonNode getRideById(String rideId) {
        if (isBlank(rideId)) {
            return null;
        }
        return internalServiceClient.get(rideServiceUrl + "/rides/" + rideId);
    }

    /** select("msg from userId createdAt file") + _id. */
    private Map<String, Object> selectMsgUser(ChatMessage m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", m.getId() != null ? m.getId().toString() : null);
        row.put("msg", m.getMsg());
        row.put("from", m.getFrom());
        row.put("userId", m.getUserId() != null ? m.getUserId().toString() : null);
        row.put("createdAt", m.getCreatedAt());
        row.put("file", m.getFile());
        return row;
    }

    /** select("msg from userId driverId rideId createdAt file") + _id. */
    private Map<String, Object> selectMsgUserDriverRide(ChatMessage m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", m.getId() != null ? m.getId().toString() : null);
        row.put("msg", m.getMsg());
        row.put("from", m.getFrom());
        row.put("userId", m.getUserId() != null ? m.getUserId().toString() : null);
        row.put("driverId", m.getDriverId() != null ? m.getDriverId().toString() : null);
        row.put("rideId", m.getRideId() != null ? m.getRideId().toString() : null);
        row.put("createdAt", m.getCreatedAt());
        row.put("file", m.getFile());
        return row;
    }

    private Map<String, Object> toFullMap(ChatMessage m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", m.getId() != null ? m.getId().toString() : null);
        row.put("rideId", m.getRideId() != null ? m.getRideId().toString() : null);
        row.put("senderId", m.getSenderId() != null ? m.getSenderId().toString() : null);
        row.put("driverId", m.getDriverId() != null ? m.getDriverId().toString() : null);
        row.put("userId", m.getUserId() != null ? m.getUserId().toString() : null);
        row.put("ticketId", m.getTicketId() != null ? m.getTicketId().toString() : null);
        row.put("from", m.getFrom());
        row.put("msg", m.getMsg());
        row.put("file", m.getFile());
        row.put("seen", m.isSeen());
        row.put("createdAt", m.getCreatedAt());
        row.put("updatedAt", m.getUpdatedAt());
        return row;
    }

    private Map<String, Object> pick(JsonNode node, String... fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            for (String f : fields) {
                out.put(f, null);
            }
            return out;
        }
        for (String f : fields) {
            JsonNode v = node.get(f);
            out.put(f, v == null || v.isNull() ? null : objectMapper.convertValue(v, Object.class));
        }
        return out;
    }

    private String toJsonArray(List<String> ids) {
        ArrayNode arr = objectMapper.createArrayNode();
        ids.forEach(arr::add);
        return arr.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "null".equals(s);
    }

    private static UUID parseUuid(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
