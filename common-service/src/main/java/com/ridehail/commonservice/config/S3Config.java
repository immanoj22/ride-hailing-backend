package com.ridehail.commonservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(S3Config.class);

    @Bean
    public S3Client s3Client(
            @Value("${ridehail.s3.access-key:}") String accessKey,
            @Value("${ridehail.s3.secret-key:}") String secretKey,
            @Value("${ridehail.s3.region:ap-south-1}") String region) {
        var builder = S3Client.builder().region(Region.of(region));
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            // No static creds configured (e.g. local/dev): fall back to the default provider chain
            // so the context still starts. Actual uploads will fail until real credentials are set.
            log.warn("ridehail.s3.access-key/secret-key not configured - S3 uploads will fail until set.");
            return builder.build();
        }
        return builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();
    }
}
