package com.ridehail.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    // Generated independently via Node's jsonwebtoken 9.x: jwt.sign({id:"abc123",role:"user"}, "shortSecret123", {expiresIn:"100y"})
    private static final String NODE_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6ImFiYzEyMyIsInJvbGUiOiJ1c2VyIiwiaWF0IjoxNzg5MDIyMjYzLCJleHAiOjQ5NDQ3ODIyNjN9.aAcvMnDgnTBF3TF8vdxdDVaYoYpNdFQdLpQF0IxWyIg";

    @Test
    void verifiesTokenSignedByNodeJsonwebtokenWithShortSecret() {
        Map<String, Object> claims = jwtService.verify(NODE_TOKEN, "shortSecret123");
        assertEquals("abc123", claims.get("id"));
        assertEquals("user", claims.get("role"));
    }

    @Test
    void tamperedTokenFailsSignatureVerification() {
        String tampered = NODE_TOKEN.substring(0, NODE_TOKEN.length() - 2) + "xx";
        assertNull(jwtService.verify(tampered, "shortSecret123"));
    }

    @Test
    void generateThenVerifyRoundTripsWithShortLegacySecret() {
        String secret = "shortSecret123"; // 14 bytes - shorter than jjwt's 256-bit HS256 minimum
        String token = jwtService.generate(Map.of("id", "abc123", "role", "user"), secret, 3600);
        Map<String, Object> claims = jwtService.verify(token, secret);
        assertEquals("abc123", claims.get("id"));
        assertEquals("user", claims.get("role"));
    }

    @Test
    void wrongSecretFailsVerification() {
        String token = jwtService.generate(Map.of("id", "abc123"), "correct-secret", 3600);
        assertNull(jwtService.verify(token, "incorrect-secret"));
    }
}
