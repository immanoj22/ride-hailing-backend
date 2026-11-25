package com.ridehail.rideservice.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reproduces the legacy authMiddleware early-exit for {@code GET /ride-apis/rides/:rideId/invoice?token=...}
 * (middlewares/ride.auth.middleware.js): Linking.openURL cannot send custom headers, so the global
 * auth is bypassed and the controller verifies the driver JWT itself.
 *
 * ridehail-common's {@link com.ridehail.common.security.RideHailAuthFilter} is not path-aware, so this runs
 * ahead of the Spring Security chain and, for that one route, injects the in-app auth headers it
 * expects (user-auth-type: ridehail-in-app + x-internal-secret) so the request passes through
 * untouched. {@code generate_ride_invoice} then does the real JWT / internal-secret check.
 */
@Configuration
public class InvoiceAuthBypassConfig {

    public static final String BROWSER_AUTH_ATTRIBUTE = "ridehail.invoiceBrowserAuth";

    private static final Pattern INVOICE_PATH = Pattern.compile("^/ride-apis/rides/[^/]+/invoice$");

    @Bean
    public FilterRegistrationBean<Filter> invoiceAuthBypassFilter(
            @Value("${ridehail.jwt.in-app-secret}") String inAppSecret) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest request = (HttpServletRequest) req;
                boolean isInvoice = "GET".equalsIgnoreCase(request.getMethod())
                        && INVOICE_PATH.matcher(request.getRequestURI()).matches()
                        && request.getParameter("token") != null
                        && isBlank(request.getHeader("user-auth-type"));
                if (isInvoice) {
                    request.setAttribute(BROWSER_AUTH_ATTRIBUTE, Boolean.TRUE);
                    chain.doFilter(new HeaderInjectingRequest(request, inAppSecret), res);
                } else {
                    chain.doFilter(req, res);
                }
            }
        });
        registration.addUrlPatterns("/ride-apis/rides/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final class HeaderInjectingRequest extends HttpServletRequestWrapper {
        private final String inAppSecret;

        HeaderInjectingRequest(HttpServletRequest request, String inAppSecret) {
            super(request);
            this.inAppSecret = inAppSecret;
        }

        private String override(String name) {
            if ("user-auth-type".equalsIgnoreCase(name)) {
                return "ridehail-in-app";
            }
            if ("x-internal-secret".equalsIgnoreCase(name)) {
                return inAppSecret;
            }
            return null;
        }

        @Override
        public String getHeader(String name) {
            String o = override(name);
            return o != null ? o : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String o = override(name);
            return o != null ? Collections.enumeration(java.util.List.of(o)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            names.add("user-auth-type");
            names.add("x-internal-secret");
            return Collections.enumeration(names);
        }
    }
}
