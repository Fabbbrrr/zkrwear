package com.zkrwatch.data.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * Port of {@code client._rsa_encrypt_password}.
 *
 * <p>{@code base64decode(publicKey)} -> import RSA public key -> PKCS#1 v1.5
 * encrypt the UTF-8 password bytes -> Base64. Mirrors PyCryptodome's
 * {@code PKCS1_v1_5.new(RSA.import_key(...))}.
 *
 * <p>RSA PKCS#1 v1.5 padding is randomized, so ciphertext is non-deterministic;
 * tests verify by decrypt round-trip rather than a fixed vector.
 *
 * <p>The supplied key is expected to be a base64 X.509 SubjectPublicKeyInfo DER
 * (what these apps ship). If a raw PKCS#1 RSAPublicKey is ever encountered,
 * {@link #encrypt} throws and we wrap it in an SPKI header — tracked as a TODO
 * to confirm against the real extracted key during M1.
 */
public final class PasswordRsa {

    private PasswordRsa() {}

    public static String encrypt(String password, String publicKeyBase64) {
        try {
            byte[] der = Base64.getDecoder().decode(publicKeyBase64.trim());
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("RSA password encryption failed", e);
        }
    }
}
