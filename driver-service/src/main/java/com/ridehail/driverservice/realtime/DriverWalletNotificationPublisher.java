package com.ridehail.driverservice.realtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.repository.DriverRepository;

/**
 * Port of driverWallet.controller.js's notifyDriverClaimUpdate(): looks up the driver's fcmToken
 * and publishes a push-notification payload to rabbit:push-notification, exactly like
 * {@code sendNotification(...)} (config/rabitmq.js). Never throws - the legacy helper only logs
 * on failure, so a notification hiccup never fails the wallet-claim request itself.
 */
@Component
public class DriverWalletNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriverWalletNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final DriverRepository driverRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DriverWalletNotificationPublisher(RabbitTemplate rabbitTemplate, DriverRepository driverRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.driverRepository = driverRepository;
    }

    public void notifyDriverClaimUpdate(UUID driverId, String status, String title, String message, UUID claimId) {
        try {
            String fcmToken = driverRepository.findById(driverId).map(Driver::getFcmToken).orElse("");

            Map<String, Object> androidData = new LinkedHashMap<>();
            androidData.put("id", claimId == null ? "" : claimId.toString());
            androidData.put("type", "wallet_claim");
            androidData.put("claimStatus", status);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", status);
            data.put("driverId", String.valueOf(driverId));
            data.put("driver", fcmToken == null ? "" : fcmToken);
            data.put("driverIds", List.of(String.valueOf(driverId)));
            data.put("title", title);
            data.put("message", message);
            data.put("androidData", androidData);

            byte[] body = objectMapper.writeValueAsBytes(data);
            rabbitTemplate.convertAndSend(DriverWalletRabbitConfig.PUSH_NOTIFICATION_QUEUE, body);
        } catch (Exception e) {
            log.error("notifyDriverClaimUpdate error", e);
        }
    }
}
