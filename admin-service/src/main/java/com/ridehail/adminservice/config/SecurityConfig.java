package com.ridehail.adminservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.EncryptedRequestFilter;
import com.ridehail.common.security.JwtService;
import com.ridehail.common.security.RideHailAuthFilter;
import com.ridehail.common.security.RideHailSecurityConfigurer;
import com.ridehail.common.security.PrincipalLookupService;
import com.ridehail.common.web.HelmetLikeHeadersFilter;
import com.ridehail.common.web.RateLimitFilter;

/**
 * Mirrors common-service's SecurityConfig, reproducing Admin-service/server.js's filter order
 * (helmet -> rateLimit -> authMiddleware -> decryptionMiddleware) with this service's own
 * PrincipalLookupService.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HelmetLikeHeadersFilter helmetFilter,
            RateLimitFilter rateLimitFilter,
            RideHailAuthFilter authFilter,
            EncryptedRequestFilter encryptedRequestFilter) throws Exception {
        return RideHailSecurityConfigurer.configure(http, helmetFilter, rateLimitFilter, authFilter, encryptedRequestFilter);
    }

    @Bean
    public HelmetLikeHeadersFilter helmetLikeHeadersFilter() {
        return new HelmetLikeHeadersFilter();
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        // matches Admin-service/server.js: windowMs 5 * 60 * 1000, max 1000
        return new RateLimitFilter(1000, 5 * 60 * 1000L);
    }

    @Bean
    public RideHailAuthFilter ridehailAuthFilter(
            JwtService jwtService,
            ApiResponseService apiResponseService,
            PrincipalLookupService principalLookupService,
            @Value("${ridehail.jwt.user-secret}") String userSecret,
            @Value("${ridehail.jwt.driver-secret}") String driverSecret,
            @Value("${ridehail.jwt.admin-secret}") String adminSecret,
            @Value("${ridehail.jwt.other-secret}") String otherSecret,
            @Value("${ridehail.jwt.in-app-secret}") String inAppSecret) {
        return new RideHailAuthFilter(jwtService, apiResponseService, principalLookupService,
                userSecret, driverSecret, adminSecret, otherSecret, inAppSecret);
    }

    @Bean
    public EncryptedRequestFilter encryptedRequestFilter(
            ApiResponseService apiResponseService,
            @Value("${ridehail.enc-secret}") String encSecret,
            @Value("${ridehail.env}") String env) {
        return new EncryptedRequestFilter(apiResponseService, encSecret, env);
    }
}
