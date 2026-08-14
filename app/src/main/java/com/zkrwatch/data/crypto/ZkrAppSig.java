package com.zkrwatch.data.crypto;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Port of {@code zeekr_ev_api/zeekr_app_sig.py} (calculate_sig / sign_request).
 *
 * <p>Signs authentication / user-center calls using {@code prod_secret}, setting
 * the {@code X-SIGNATURE} header. Verified byte-for-byte against golden vectors
 * captured from the real Python library (see CryptoParityTest).
 *
 * <p>Signature base string = canonicalHeaders + (query + "\n")? + (bodyMd5 + "\n")?
 * + METHOD + "\n" + rawPath. Then Base64(HMAC-SHA256(prod_secret, base)).
 */
public final class ZkrAppSig {

    private static final List<String> ALLOWED_HEADERS = Arrays.asList(
            "x-app-id",
            "content-type",
            "x-api-signature-nonce",
            "x-timestamp",
            "x-api-signature-version",
            "x-project-id",
            "authorization",
            "accept-language",
            "x-vin",
            "x-device-id",
            "x-platform");

    private static final String X_VIN_HEADER = "x-vin";
    private static final String AUTH_HEADER = "authorization";

    private ZkrAppSig() {}

    /**
     * Compute the X-SIGNATURE value. {@code headers} must already contain every
     * header that will be sent (including x-api-signature-nonce and x-timestamp),
     * exactly as the reference does before calling calculate_sig.
     */
    public static String calculateSig(String method, String url,
                                      Map<String, String> headers,
                                      String body, String secret) {
        URI uri = URI.create(url);
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        String rawQuery = uri.getRawQuery();

        // 1) Canonical headers: filter, lowercase key, sort by key, "k:v\n".
        List<String[]> filtered = new ArrayList<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String lower = e.getKey().toLowerCase(Locale.ROOT);
            String value = e.getValue() == null ? "" : e.getValue();
            if (validateHeader(lower, value)) {
                filtered.add(new String[] {lower, value});
            }
        }
        Collections.sort(filtered, (a, b) -> a[0].compareTo(b[0]));
        StringBuilder headerString = new StringBuilder();
        for (String[] kv : filtered) {
            headerString.append(kv[0]).append(':').append(kv[1]).append('\n');
        }

        // 2) Canonical query: parse_qs (decoded), sort by key (case-sensitive),
        //    re-encode value (%2F->/, %3F->?, *->%2A).
        String queryString = canonicalQuery(rawQuery);

        // 3) Body MD5 (only when content-type contains application/json).
        String bodyHashB64 = "";
        String contentType = getIgnoreCase(headers, "content-type");
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")
                && body != null && !body.isEmpty()) {
            try {
                String canonicalJson = CanonicalJson.canonicalize(body);
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] hash = md5.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
                bodyHashB64 = Base64.getEncoder().encodeToString(hash);
            } catch (Exception ignored) {
                // Reference swallows body-processing errors (non-JSON despite header).
            }
        }

        // 4) Assemble signature base string.
        StringBuilder base = new StringBuilder();
        if (headerString.length() > 0) base.append(headerString);
        if (!queryString.isEmpty()) base.append(queryString).append('\n');
        if (!bodyHashB64.isEmpty()) base.append(bodyHashB64).append('\n');
        base.append(method.toUpperCase(Locale.ROOT)).append('\n');
        base.append(rstrip(rawPath));

        // 5) HMAC-SHA256(prod_secret, base) -> Base64.
        return hmacSha256Base64(base.toString(), secret);
    }

    private static boolean validateHeader(String lowerKey, String value) {
        if (!ALLOWED_HEADERS.contains(lowerKey)) return false;
        if (lowerKey.equals(X_VIN_HEADER)) return !value.isEmpty();
        // authorization must be non-empty; all other allowed headers pass.
        return !lowerKey.equals(AUTH_HEADER) || !value.isEmpty();
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        // parse_qs(keep_blank_values=True): decode keys/values, keep blanks,
        // last value wins (we only use the first per key, like the reference).
        Map<String, String> firstValue = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
            String rawVal = eq >= 0 ? pair.substring(eq + 1) : "";
            String key = urlDecode(rawKey);
            String val = urlDecode(rawVal);
            if (!firstValue.containsKey(key)) {
                firstValue.put(key, val);
            }
        }
        List<String> keys = new ArrayList<>(firstValue.keySet());
        Collections.sort(keys); // case-sensitive, matching Python sorted(items())
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = firstValue.get(k);
            String encoded = v.replace("%2F", "/").replace("%3F", "?").replace("*", "%2A");
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(encoded);
        }
        return sb.toString();
    }

    /** URL-decode matching urllib.parse.parse_qs (unquote_plus: '+' -> space). */
    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** Python str.rstrip(): remove trailing whitespace (not slashes). */
    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    private static String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }
}
