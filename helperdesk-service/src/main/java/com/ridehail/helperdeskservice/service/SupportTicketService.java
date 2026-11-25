package com.ridehail.helperdeskservice.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.realtime.SocketRelayPublisher;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.helperdeskservice.dto.AddMessageRequest;
import com.ridehail.helperdeskservice.dto.CreateSupportTicketRequest;
import com.ridehail.helperdeskservice.dto.SupportTicketResponse;
import com.ridehail.helperdeskservice.entity.SupportMessage;
import com.ridehail.helperdeskservice.entity.SupportTicket;
import com.ridehail.helperdeskservice.repository.SupportTicketRepository;

/**
 * "support-tickets" bucket - direct port of helperdesk.controller.js's createSupportTicket /
 * addMessageOnTicket / getSupportTicketsByUser / getSupportTicketsByAdmin /
 * closeSupportTicketByAdmin / reopenSupportTicketByAdmin.
 */
@Service
@Transactional(readOnly = true)
public class SupportTicketService {

    private static final String NEW_SUPPORT_MESSAGE_EVENT = "support:new-message";

    private final SupportTicketRepository supportTicketRepository;
    private final InternalServiceClient internalServiceClient;
    private final SocketRelayPublisher socketRelayPublisher;
    private final ObjectMapper objectMapper;
    private final RestTemplate rawRestTemplate;
    private final String commonServiceUrl;
    private final String userServiceUrl;
    private final String driverServiceUrl;
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public SupportTicketService(
            SupportTicketRepository supportTicketRepository,
            InternalServiceClient internalServiceClient,
            SocketRelayPublisher socketRelayPublisher,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ridehail.services.common-url}") String commonServiceUrl,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl,
            @Value("${ridehail.jwt.in-app-secret}") String inAppSecret,
            @Value("${ridehail.enc-secret}") String encSecret,
            @Value("${ridehail.env}") String env) {
        this.supportTicketRepository = supportTicketRepository;
        this.internalServiceClient = internalServiceClient;
        this.socketRelayPublisher = socketRelayPublisher;
        this.objectMapper = objectMapper;
        this.rawRestTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.commonServiceUrl = commonServiceUrl;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    // ---------------------------------------------------------------------------------------
    // POST /create-support-ticket
    // ---------------------------------------------------------------------------------------

    @Transactional
    public SupportTicketResponse createSupportTicket(CreateSupportTicketRequest request, MultipartFile supportImage) {
        AuthContext ctx = AuthContext.current();
        UUID userId = ctx != null && ctx.authUserType() == AuthUserType.USER ? uuid(ctx.principalId()) : null;
        UUID driverId = ctx != null && ctx.authUserType() == AuthUserType.DRIVER ? uuid(ctx.principalId()) : null;
        if (userId == null && driverId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "user id required");
        }

        String imageUrl = supportImage != null && !supportImage.isEmpty() ? uploadSupportImage(supportImage) : null;

        SupportTicket ticket = SupportTicket.builder()
                .userId(userId != null ? userId : driverId)
                .subject(orEmpty(request.getSubject()))
                .fromType(userId != null ? "user" : "driver")
                .complainType(request.getComplainType())
                .build();
        SupportMessage first = SupportMessage.builder()
                .supportTicket(ticket)
                .message(orEmpty(request.getMessage()))
                .image(orEmpty(imageUrl))
                .sender("creator")
                .build();
        ticket.getMessages().add(first);
        ticket = supportTicketRepository.save(ticket);

        return SupportTicketResponse.from(ticket);
    }

    // ---------------------------------------------------------------------------------------
    // PUT /add-message
    // ---------------------------------------------------------------------------------------

    @Transactional
    public SupportTicketResponse addMessageOnTicket(AddMessageRequest request, MultipartFile supportImage) {
        SupportTicket ticket = supportTicketRepository.findById(uuid(request.getSupportTicketId()))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Support ticket is not updated."));

        String imageUrl = supportImage != null && !supportImage.isEmpty() ? uploadSupportImage(supportImage) : null;

        SupportMessage message = SupportMessage.builder()
                .supportTicket(ticket)
                .message(orEmpty(request.getMessage()))
                .image(orEmpty(imageUrl))
                .sender(orEmpty(request.getSender()))
                .build();
        ticket.getMessages().add(message);
        ticket = supportTicketRepository.save(ticket);

        SupportMessage latest = ticket.getMessages().get(ticket.getMessages().size() - 1);
        Map<String, Object> socketPayload = new LinkedHashMap<>();
        socketPayload.put("ticketId", ticket.getId());
        socketPayload.put("message", Map.of(
                "message", latest.getMessage(), "image", latest.getImage(),
                "sender", latest.getSender(), "createdAt", latest.getCreatedAt()));

        if ("admin".equals(request.getSender())) {
            socketRelayPublisher.toUser(String.valueOf(ticket.getUserId()), NEW_SUPPORT_MESSAGE_EVENT, socketPayload);
        } else {
            socketRelayPublisher.toAdmins(NEW_SUPPORT_MESSAGE_EVENT, socketPayload);
        }

        return SupportTicketResponse.from(ticket);
    }

    // ---------------------------------------------------------------------------------------
    // GET /get-support-tickets-by-user
    // ---------------------------------------------------------------------------------------

    public Map<String, Object> getSupportTicketsByUser(Integer limit, Integer skip) {
        AuthContext ctx = AuthContext.current();
        UUID userId = ctx != null && ctx.authUserType() == AuthUserType.USER ? uuid(ctx.principalId()) : null;
        UUID driverId = ctx != null && ctx.authUserType() == AuthUserType.DRIVER ? uuid(ctx.principalId()) : null;
        if (userId == null && driverId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "User ID required");
        }

        UUID ownerId = userId != null ? userId : driverId;
        String fromType = userId != null ? "user" : "driver";
        int pageSize = (limit == null || limit <= 0) ? Integer.MAX_VALUE : limit;
        int pageNumber = (skip == null || pageSize == Integer.MAX_VALUE) ? 0 : skip / pageSize;

        List<SupportTicket> tickets = supportTicketRepository.findByUserIdAndFromType(ownerId, fromType,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        if (tickets.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No support tickets found");
        }
        return Map.of("list", tickets.stream().map(SupportTicketResponse::from).toList());
    }

    // ---------------------------------------------------------------------------------------
    // GET /get-support-tickets-by-admin
    // ---------------------------------------------------------------------------------------

    public Map<String, Object> getSupportTicketsByAdmin(Integer page, Integer limit, String complainType,
            String status, String search) {
        int p = page == null ? 1 : page;
        int l = limit == null ? 20 : limit;

        List<SupportTicket> all = supportTicketRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<SupportTicket> filtered = new ArrayList<>();
        for (SupportTicket t : all) {
            if (complainType != null && !complainType.equals(t.getComplainType())) {
                continue;
            }
            if (status != null && !status.isBlank()) {
                boolean wantOpen = "open".equals(status);
                if (!t.getStatus().equals(wantOpen)) {
                    continue;
                }
            }
            filtered.add(t);
        }

        List<SupportTicketResponse> enriched = new ArrayList<>();
        for (SupportTicket t : filtered) {
            JsonNode user = null;
            JsonNode driver = null;
            if ("user".equals(t.getFromType()) && t.getUserId() != null) {
                JsonNode resp = internalServiceClient.get(userServiceUrl + "/profile/" + t.getUserId());
                user = resp == null ? null : resp.path("data").path("user");
            } else if (t.getUserId() != null) {
                JsonNode resp = internalServiceClient.get(driverServiceUrl + "/profile/" + t.getUserId());
                driver = resp == null ? null : resp.path("data");
            }
            enriched.add(SupportTicketResponse.from(t, blankToNull(user), blankToNull(driver)));
        }

        if (search != null && !search.isBlank()) {
            String needle = search.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            enriched = enriched.stream().filter(t -> adminSearchMatch(t, needle)).toList();
        }

        int calcSkip = (p - 1) * l;
        List<SupportTicketResponse> pageSlice = calcSkip >= enriched.size() ? List.of()
                : enriched.subList(calcSkip, Math.min(calcSkip + l, enriched.size()));

        return Map.of("list", pageSlice, "pagination",
                Map.of("total", filtered.size(), "page", p, "limit", l));
    }

    // ---------------------------------------------------------------------------------------
    // DELETE /close-support-ticket   PATCH /reopen-support-ticket
    // ---------------------------------------------------------------------------------------

    @Transactional
    public SupportTicketResponse closeSupportTicketByAdmin(String ticketId, String closingMessage) {
        SupportTicket ticket = supportTicketRepository.findById(uuid(ticketId))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Ticket not found"));
        if (!Boolean.TRUE.equals(ticket.getStatus())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ticket already closed");
        }
        ticket.setClosingMessage(closingMessage);
        ticket.setStatus(false);
        return SupportTicketResponse.from(supportTicketRepository.save(ticket));
    }

    @Transactional
    public SupportTicketResponse reopenSupportTicketByAdmin(String ticketId) {
        SupportTicket ticket = supportTicketRepository.findById(uuid(ticketId))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Ticket not found"));
        if (Boolean.TRUE.equals(ticket.getStatus())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ticket already open");
        }
        ticket.setClosingMessage("");
        ticket.setStatus(true);
        return SupportTicketResponse.from(supportTicketRepository.save(ticket));
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private boolean adminSearchMatch(SupportTicketResponse t, String needle) {
        String driverName = t.driver() == null ? "" : (t.driver().path("firstName").asText("") + " "
                + t.driver().path("lastName").asText("")).toLowerCase(Locale.ROOT).trim();
        String userName = t.user() == null ? "" : firstNonBlank(t.user().path("fullname").asText(""),
                t.user().path("firstname").asText("")).toLowerCase(Locale.ROOT);
        boolean inMessages = t.messages() != null && t.messages().stream()
                .anyMatch(m -> m.message() != null && m.message().toLowerCase(Locale.ROOT).contains(needle));
        return driverName.contains(needle) || userName.contains(needle)
                || (t.subject() != null && t.subject().toLowerCase(Locale.ROOT).contains(needle))
                || (t.fromType() != null && t.fromType().toLowerCase(Locale.ROOT).contains(needle))
                || inMessages;
    }

    private String uploadSupportImage(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("imageFiles", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("allSame", "true");
            body.add("sameFolderName", "public_support_ticket");
            String rawBody = rawRestTemplate.exchange(commonServiceUrl + "/image-upload", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class).getBody();
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            JsonNode response = objectMapper.readTree(json);
            if (!response.path("status").asBoolean(false) || !response.path("data").isArray() || response.path("data").isEmpty()) {
                return null;
            }
            return response.path("data").get(0).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode blankToNull(JsonNode node) {
        return (node == null || node.isMissingNode() || node.isNull()
                || (node.isObject() && node.isEmpty())) ? null : node;
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid id");
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
