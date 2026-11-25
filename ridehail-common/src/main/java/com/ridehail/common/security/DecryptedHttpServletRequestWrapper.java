package com.ridehail.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Replaces the request body and/or query parameters with the decrypted {@code reqData} payload,
 * so downstream {@code @RequestBody}/{@code @RequestParam} binding sees the same shape it would
 * have without encryption - matching the legacy {@code decryptionMiddleware} replacing
 * {@code req.body}/{@code req.query} wholesale.
 */
class DecryptedHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] bodyBytes;
    private final Map<String, String[]> overrideParams;

    DecryptedHttpServletRequestWrapper(HttpServletRequest request, byte[] bodyBytes, Map<String, Object> decryptedQuery) {
        super(request);
        this.bodyBytes = bodyBytes;
        this.overrideParams = decryptedQuery == null ? null : toParamMap(decryptedQuery);
    }

    private static Map<String, String[]> toParamMap(Map<String, Object> data) {
        Map<String, String[]> params = new LinkedHashMap<>();
        data.forEach((k, v) -> params.put(k, new String[] { v == null ? null : String.valueOf(v) }));
        return params;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (bodyBytes == null) {
            return super.getInputStream();
        }
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bodyBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public java.io.BufferedReader getReader() throws IOException {
        if (bodyBytes == null) {
            return super.getReader();
        }
        return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() {
        return bodyBytes == null ? super.getContentType() : HttpHeaders.CONTENT_TYPE + ": application/json";
    }

    @Override
    public String getParameter(String name) {
        if (overrideParams != null) {
            String[] values = overrideParams.get(name);
            return values == null ? null : values[0];
        }
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return overrideParams != null ? Collections.unmodifiableMap(overrideParams) : super.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return overrideParams != null ? Collections.enumeration(overrideParams.keySet()) : super.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return overrideParams != null ? overrideParams.get(name) : super.getParameterValues(name);
    }
}
