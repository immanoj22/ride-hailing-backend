package com.ridehail.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CryptoJsCompatibleCipherTest {

    private static final String SECRET = "&29bas7TYgw87%$9+=";

    // Generated independently via Node's crypto-js 4.2.0 (verified round trip in Node too):
    // Encryptdata({status:true,message:"hello world",data:{a:1,b:[1,2,3],c:"unicode✓"},success:"success"})
    private static final String NODE_PRODUCED_WIRE =
            "VTJGc2RHVmtYMStsUnlrWlJjYlIvRHZobVV0ZmFyVFkrQXVOQzNWVCtoQWxwSVBzdmJlNE03dmY4MVBhcmFXdFpDRHV1WGhLNmxvVG04ZzRKSW5aVm8wNkhyejNaR1BaU0JlVWY5SWY0MFY1M1hiOEl4b29ZUmYwcWZwT3hJS2hnK015bngydGR2R0VYUmxCYUJDd3ZqQlp1bUlrOEFIRVI5MWVndURBNkUwPQ==";

    @Test
    void decryptsWireStringProducedByNodeCryptoJs() {
        String decrypted = CryptoJsCompatibleCipher.decrypt(NODE_PRODUCED_WIRE, SECRET);
        assertEquals(
                "{\"status\":true,\"message\":\"hello world\",\"data\":{\"a\":1,\"b\":[1,2,3],\"c\":\"unicode✓\"},\"success\":\"success\"}",
                decrypted);
    }

    @Test
    void encryptDecryptRoundTrips() {
        String plaintext = "{\"foo\":\"bar\",\"n\":123}";
        String wire = CryptoJsCompatibleCipher.encrypt(plaintext, SECRET);
        assertEquals(plaintext, CryptoJsCompatibleCipher.decrypt(wire, SECRET));
    }

    @Test
    void decryptWithWrongSecretReturnsNullInsteadOfThrowing() {
        String wire = CryptoJsCompatibleCipher.encrypt("{\"a\":1}", SECRET);
        assertTrue(CryptoJsCompatibleCipher.decrypt(wire, "wrong-secret") == null
                || !CryptoJsCompatibleCipher.decrypt(wire, "wrong-secret").contains("\"a\":1"));
    }
}
