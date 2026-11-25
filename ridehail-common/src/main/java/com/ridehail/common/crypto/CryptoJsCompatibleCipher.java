package com.ridehail.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Byte-for-byte reimplementation of the legacy Node {@code Encryptdata}/{@code Decryptdata}
 * helpers (common-service/config/common_functions.js), which every RideHail service and the mobile
 * clients rely on for the response/request wire format. This MUST stay wire-compatible or every
 * client breaks, so do not "clean up" the double base64 layer below - it matches the original:
 *
 * <pre>
 * Encryptdata(data):
 *   encJson = CryptoJS.AES.encrypt(JSON.stringify(data), SECRET).toString()   // OpenSSL "Salted__" format, base64
 *   return Base64(UTF8Bytes(encJson))                                        // a SECOND, outer base64 layer
 *
 * Decryptdata(data):
 *   decData = UTF8String(Base64Decode(data))                                 // undo outer layer -> openssl b64 string
 *   plaintext = CryptoJS.AES.decrypt(decData, SECRET)                        // openssl KDF decrypt
 *   return JSON.parse(plaintext)
 * </pre>
 *
 * CryptoJS derives the AES-256-CBC key/IV from a passphrase using the OpenSSL EVP_BytesToKey
 * (MD5, single digest round) key derivation, prefixing ciphertext with {@code "Salted__" + salt}.
 */
public final class CryptoJsCompatibleCipher {

    private static final byte[] SALTED_MAGIC = "Salted__".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LEN = 8;
    private static final int KEY_LEN = 32; // AES-256
    private static final int IV_LEN = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoJsCompatibleCipher() {
    }

    /** Equivalent of {@code Encryptdata(plaintextJson)}. Returns the double-base64 wire string. */
    public static String encrypt(String plaintext, String passphrase) {
        try {
            byte[] salt = new byte[SALT_LEN];
            RANDOM.nextBytes(salt);
            byte[][] keyAndIv = evpBytesToKey(passphrase.getBytes(StandardCharsets.UTF_8), salt, KEY_LEN, IV_LEN);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyAndIv[0], "AES"), new IvParameterSpec(keyAndIv[1]));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] openSslPayload = concat(SALTED_MAGIC, salt, cipherBytes);
            String openSslBase64 = Base64.getEncoder().encodeToString(openSslPayload);

            return Base64.getEncoder().encodeToString(openSslBase64.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt payload", e);
        }
    }

    /**
     * Equivalent of {@code Decryptdata(wireString)}. Returns the decrypted JSON plaintext, or
     * {@code null} on any failure (blank input, bad padding, wrong secret) - matching the Node
     * helper's {@code catch { return '' }} behaviour so callers can null-check instead of
     * exploding on malformed/foreign payloads.
     */
    public static String decrypt(String wireString, String passphrase) {
        if (wireString == null || wireString.isBlank()) {
            return wireString;
        }
        try {
            byte[] outerDecoded = Base64.getDecoder().decode(wireString);
            String openSslBase64 = new String(outerDecoded, StandardCharsets.UTF_8);
            byte[] openSslPayload = Base64.getDecoder().decode(openSslBase64);

            if (openSslPayload.length < SALT_LEN + SALT_LEN || !matchesMagic(openSslPayload)) {
                return null;
            }
            byte[] salt = Arrays.copyOfRange(openSslPayload, SALT_LEN, SALT_LEN + SALT_LEN);
            byte[] cipherBytes = Arrays.copyOfRange(openSslPayload, SALT_LEN + SALT_LEN, openSslPayload.length);

            byte[][] keyAndIv = evpBytesToKey(passphrase.getBytes(StandardCharsets.UTF_8), salt, KEY_LEN, IV_LEN);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndIv[0], "AES"), new IvParameterSpec(keyAndIv[1]));
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchesMagic(byte[] payload) {
        for (int i = 0; i < SALTED_MAGIC.length; i++) {
            if (payload[i] != SALTED_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** OpenSSL EVP_BytesToKey with MD5, single digest per block - matches CryptoJS's default KDF. */
    private static byte[][] evpBytesToKey(byte[] password, byte[] salt, int keyLen, int ivLen) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] result = new byte[keyLen + ivLen];
        byte[] prev = new byte[0];
        int generated = 0;
        while (generated < result.length) {
            md5.reset();
            md5.update(prev);
            md5.update(password);
            md5.update(salt);
            prev = md5.digest();
            int toCopy = Math.min(prev.length, result.length - generated);
            System.arraycopy(prev, 0, result, generated, toCopy);
            generated += toCopy;
        }
        return new byte[][] { Arrays.copyOfRange(result, 0, keyLen), Arrays.copyOfRange(result, keyLen, keyLen + ivLen) };
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += a.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }
}
