package com.ridehail.socketservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.socketservice.dto.SaveChatRequest;
import com.ridehail.socketservice.service.ChatHistoryService;

/**
 * Port of controller/socket.controller.js + Routes/socket.routes.js, mounted at
 * {@code app.use('/socket-apis', routes)} in server.js. Paths and HTTP methods are preserved
 * exactly; all logic lives in {@link ChatHistoryService}.
 */
@RestController
@RequestMapping("/socket-apis")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;
    private final ApiResponseService apiResponseService;

    public ChatHistoryController(ChatHistoryService chatHistoryService, ApiResponseService apiResponseService) {
        this.chatHistoryService = chatHistoryService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping(value = "/saveChat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveChatMessage(
            @RequestParam("rideId") String rideId,
            @RequestParam("senderId") String senderId,
            @RequestParam("from") String from,
            @RequestParam(value = "msg", required = false) String msg,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        SaveChatRequest request = new SaveChatRequest();
        request.setRideId(rideId);
        request.setSenderId(senderId);
        request.setFrom(from);
        request.setMsg(msg);
        Map<String, Object> data = chatHistoryService.saveChatMessage(request, file);
        return apiResponseService.ok("Message added successfully", data);
    }

    @GetMapping("/get-ride-chat/{rideId}")
    public ResponseEntity<?> getRideMessage(@PathVariable String rideId) {
        List<Map<String, Object>> data = chatHistoryService.getRideMessage(rideId);
        return apiResponseService.ok("Chats are available", data);
    }

    @GetMapping("/get-driver-admin-chat/{rideId}/{driverId}")
    public ResponseEntity<?> getChatMessage(@PathVariable String rideId, @PathVariable String driverId) {
        List<Map<String, Object>> data = chatHistoryService.getChatMessage(rideId, driverId);
        return apiResponseService.ok("Chats are available", data);
    }

    @GetMapping("/get-chat")
    public ResponseEntity<?> getMessageFromUser(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "rideId", required = false) String rideId,
            @RequestParam(value = "driverId", required = false) String driverId) {
        List<Map<String, Object>> data = chatHistoryService.getMessageFromUser(userId, rideId, driverId);
        return apiResponseService.ok("Chats are available for the user", data);
    }

    @GetMapping("/get-message-by-rideid/{rideId}")
    public ResponseEntity<?> getMessageByRideId(@PathVariable String rideId) {
        List<Map<String, Object>> data = chatHistoryService.getMessageByRideId(rideId);
        return apiResponseService.ok("Chats are available", data);
    }

    @GetMapping("/get-chat-by-ticketid/{ticketId}")
    public ResponseEntity<?> getChatUsingTicketId(@PathVariable String ticketId) {
        List<Map<String, Object>> data = chatHistoryService.getChatUsingTicketId(ticketId);
        return apiResponseService.ok("Chats are available", data);
    }
}
