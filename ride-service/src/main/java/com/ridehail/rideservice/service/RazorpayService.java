package com.ridehail.rideservice.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;

/** Ports ride.controller.js: create_razorpay_order, verify_razorpay_payment. */
@Service
public class RazorpayService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String keyId;
    private final String keySecret;

    public RazorpayService(@Value("${ridehail.razorpay.key-id:}") String keyId,
            @Value("${ridehail.razorpay.key-secret:}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public Map<String, Object> createOrder(Double amount, String currency) {
        String cur = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
        long amountInPaise = Math.round((amount == null ? 0d : amount) * 100);
        if (amountInPaise <= 0) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid amount");
        }

        Map<String, Object> orderPayload = new LinkedHashMap<>();
        orderPayload.put("amount", amountInPaise);
        orderPayload.put("currency", cur);

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(orderPayload)))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = resp.body() == null || resp.body().isBlank()
                    ? mapper.createObjectNode() : mapper.readTree(resp.body());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String desc = body.path("error").path("description").asText(null);
                throw new ApiException(StatusCode.BAD_GATEWAY,
                        desc != null ? desc : "Failed to create Razorpay order");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("keyId", keyId);
            data.put("order", body);
            return data;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_GATEWAY, "Failed to create Razorpay order");
        }
    }

    public Map<String, Object> verifyPayment(String orderId, String paymentId, String signature) {
        if (keySecret == null || keySecret.isBlank()) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Razorpay secret is not configured");
        }
        String expected = hmacSha256Hex(orderId + "|" + paymentId, keySecret);
        boolean valid = expected.equals(signature);
        if (!valid) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid Razorpay payment signature");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verified", true);
        data.put("razorpay_order_id", orderId);
        data.put("razorpay_payment_id", paymentId);
        return data;
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to verify signature");
        }
    }
}
