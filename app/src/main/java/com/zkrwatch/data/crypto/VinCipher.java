package com.zkrwatch.data.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Port of {@code zeekr_app_sig.aes_encrypt}, used by {@code client._get_encrypted_vin}.
 *
 * <p>AES/CBC/PKCS5Padding. The key and IV are the 16-character config strings used
 * as their raw UTF-8 bytes (the Python names them {@code *_hex} but calls
 * {@code key_hex.encode("utf-8")} — they are NOT hex-decoded), giving AES-128.
 * Output is Base64. Deterministic; cross-checked against OpenSSL in tests.
 *
 * <p>Sent as the {@code X-VIN} header on every vehicle-scoped request.
 */
public final class VinCipher {

    private VinCipher() {}

    public static String encrypt(String vin, String key, String iv) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new IvParameterSpec(ivBytes));
            byte[] out = cipher.doFinal(vin.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("VIN AES encryption failed", e);
        }
    }
}
