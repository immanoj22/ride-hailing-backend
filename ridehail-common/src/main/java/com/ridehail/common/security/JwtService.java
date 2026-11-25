package com.ridehail.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dependency-free HS256 JWT sign/verify, deliberately NOT using the jjwt library:
 * jjwt's {@code Keys.hmacShaKeyFor} (and its signature algorithms) reject HMAC keys shorter than
 * 256 bits, but this project's legacy secrets (USER_JWT_SECRET etc, carried over from the Node
 * {@code jsonwebtoken} library) are short, human-chosen strings. Node's {@code jsonwebtoken}
 * places no such restriction on HMAC-SHA256 key length, so a jjwt-based implementation would
 * throw on every real secret in this codebase and be unable to verify tokens issued before the
 * migration. This mirrors {@code jwt.sign}/{@code jwt.verify} exactly instead.
 */
@Component
public class JwtService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Base64.Encoder URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DEC = Base64.getUrlDecoder();

    public Map<String, Object> verify(String token, String secret) {
        try {
            if (token == null) {
                return null;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            byte[] expectedSig = hmac(parts[0] + "." + parts[1], secret);
            byte[] actualSig = URL_DEC.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(URL_DEC.decode(parts[1]), Map.class);

            Object exp = claims.get("exp");
            if (exp instanceof Number expNumber && Instant.now().getEpochSecond() >= expNumber.longValue()) {
                return null; // expired
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    public String generate(Map<String, Object> claims, String secret, long expiresInSeconds) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>(claims);
            long now = Instant.now().getEpochSecond();
            payload.put("iat", now);
            if (expiresInSeconds > 0) {
                payload.put("exp", now + expiresInSeconds);
            }

            String headerB64 = URL_ENC.encodeToString(objectMapper.writeValueAsBytes(header));
            String payloadB64 = URL_ENC.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerB64 + "." + payloadB64;
            String signature = URL_ENC.encodeToString(hmac(signingInput, secret));

            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private byte[] hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
