package com.ridehail.common.security;

import java.util.List;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ridehail.common.web.HelmetLikeHeadersFilter;
import com.ridehail.common.web.RateLimitFilter;

/**
 * Shared filter-chain wiring for every service: CORS wide open (matches the Node {@code cors()}
 * default), stateless sessions, no Spring Security authentication mechanics (auth is fully custom
 * via {@link RideHailAuthFilter}), and the same filter order as server.js:
 * {@code helmet -> rateLimit -> authMiddleware -> decryptionMiddleware}.
 */
public final class RideHailSecurityConfigurer {

    private RideHailSecurityConfigurer() {
    }

    public static SecurityFilterChain configure(
            HttpSecurity http,
            HelmetLikeHeadersFilter helmetFilter,
            RateLimitFilter rateLimitFilter,
            RideHailAuthFilter authFilter,
            EncryptedRequestFilter encryptedRequestFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(helmetFilter, RateLimitFilter.class)
                .addFilterAfter(authFilter, HelmetLikeHeadersFilter.class)
                .addFilterAfter(encryptedRequestFilter, RideHailAuthFilter.class);
        return http.build();
    }

    public static CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
