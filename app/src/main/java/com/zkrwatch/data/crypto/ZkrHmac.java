package com.zkrwatch.data.crypto;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Port of {@code zeekr_ev_api/zeekr_hmac.py} (generateHMAC).
 *
 * <p>Signs vehicle-data API calls. Produces the five {@code X-HMAC-*}/{@code X-DATE}
 * headers the Zkr gateway requires. Verified byte-for-byte against golden
 * vectors captured from the real Python library (see CryptoParityTest).
 *
 * <p>Critical parity details, straight from the source:
 * <ul>
 *   <li>Sign string = METHOD\npath\nquery\naccessKey\ngmtDate, then the signature
 *       is HMAC over that string <b>plus one more trailing "\n"</b>.</li>
 *   <li>Query is parsed from the raw (still URL-encoded) query string and sorted
 *       case-insensitively by key.</li>
 *   <li>Body digest = HMAC-SHA256(secret, bodyStringUtf8); empty/absent body =&gt; "".</li>
 * </ul>
 */
public final class ZkrHmac {

    // Java equivalent of Python "%a, %d %b %Y %H:%M:%S GMT" (Locale.US, UTC).
    private static final String DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    private ZkrHmac() {}

    /** Sign using the current UTC time. */
    public static Map<String, String> sign(String method, String url, String body,
                                           String accessKey, String secretKey) {
        return sign(method, url, body, accessKey, secretKey, gmtNow());
    }

    /** Sign with an explicit GMT date string (used by parity tests). */
    public static Map<String, String> sign(String method, String url, String body,
                                           String accessKey, String secretKey,
                                           String gmtDate) {
        URI uri = URI.create(url);
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        String rawQuery = uri.getRawQuery(); // null if absent

        String canonicalPath = canonicalPath(rawPath);
        String canonicalQuery = canonicalQuery(rawQuery);

        String signString = String.join("\n",
                method.toUpperCase(Locale.ROOT),
                canonicalPath,
                canonicalQuery,
                accessKey,
                gmtDate);

        String signature = hmacSha256Base64(signString + "\n", secretKey);
        String bodyContent = body == null ? "" : body;
        String bodyDigest = hmacSha256Base64(bodyContent, secretKey);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-HMAC-ALGORITHM", "hmac-sha256");
        headers.put("X-HMAC-SIGNATURE", signature);
        headers.put("X-HMAC-ACCESS-KEY", accessKey);
        headers.put("X-HMAC-DIGEST", bodyDigest);
        headers.put("X-DATE", gmtDate);
        return headers;
    }

    static String gmtNow() {
        SimpleDateFormat fmt = new SimpleDateFormat(DATE_PATTERN, Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    /** "/" + non-empty segments of path (strip("/").split("/")); "/" if empty. */
    private static String canonicalPath(String rawPath) {
        String stripped = strip(rawPath, '/');
        if (stripped.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String seg : stripped.split("/", -1)) {
            if (!seg.isEmpty()) sb.append('/').append(seg);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    /** Parse raw query (split on '&' then first '='), sort keys case-insensitively. */
    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        // Mirror Python dict build: last value wins per key.
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                params.put(key, value);
            }
        }
        if (params.isEmpty()) return "";
        List<String> keys = new ArrayList<>(params.keySet());
        keys.sort((a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT)));
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k : keys) {
            if (!first) sb.append('&');
            first = false;
            sb.append(k).append('=').append(params.get(k));
        }
        return sb.toString();
    }

    static String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String strip(String s, char c) {
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == c) start++;
        while (end > start && s.charAt(end - 1) == c) end--;
        return s.substring(start, end);
    }
}
