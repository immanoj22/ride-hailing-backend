package com.ridehail.common.web;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mirrors every service's {@code helmet({ contentSecurityPolicy: false,
 * crossOriginEmbedderPolicy: false, crossOriginOpenerPolicy: false,
 * crossOriginResourcePolicy: {policy:"cross-origin"}, frameguard: {action:"deny"}, xssFilter: true })}
 * call. CSP/COEP/COOP are deliberately left unset, same as the Node config.
 */
public class HelmetLikeHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-DNS-Prefetch-Control", "off");
        response.setHeader("X-Download-Options", "noopen");
        chain.doFilter(request, response);
    }
}
