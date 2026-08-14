package com.zkrwatch.data.crypto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonicalizes a JSON string to match Python's
 * {@code json.dumps(json.loads(body), sort_keys=True, separators=(",", ":"))}
 * with the default {@code ensure_ascii=True}.
 *
 * <p>This must be byte-for-byte identical to what the reference
 * {@code zeekr_app_sig.calculate_sig} produces before MD5-hashing the body,
 * otherwise the {@code X-SIGNATURE} on every app-signed (auth) request diverges
 * and the Zkr backend rejects login. See {@link ZkrAppSig}.
 *
 * <p>Supported value types (the only ones the app ever sends): object, array,
 * string, integer, boolean, null. Non-integer numbers are preserved via their
 * original token text; the app never sends floats in signed bodies (temps and
 * durations are sent as strings), so Python float-repr parity is intentionally
 * out of scope and asserted against in tests.
 */
public final class CanonicalJson {

    private CanonicalJson() {}

    /** Parse {@code json} and re-emit it in Python-canonical form. */
    public static String canonicalize(String json) {
        Parser p = new Parser(json);
        Object tree = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("Trailing content in JSON at index " + p.pos);
        }
        StringBuilder sb = new StringBuilder();
        emit(tree, sb);
        return sb.toString();
    }

    // ---- Emitter (mirrors Python json.dumps semantics) ----

    @SuppressWarnings("unchecked")
    private static void emit(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map) {
            // TreeMap already orders keys by String.compareTo == Unicode code-unit
            // order, matching Python's default sort_keys for ASCII/BMP keys.
            Map<String, Object> m = (Map<String, Object>) v;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                emitString(e.getKey(), sb);
                sb.append(':');
                emit(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            sb.append('[');
            boolean first = true;
            for (Object item : l) {
                if (!first) sb.append(',');
                first = false;
                emit(item, sb);
            }
            sb.append(']');
        } else if (v instanceof String) {
            emitString((String) v, sb);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof BigInteger) {
            sb.append(v.toString());
        } else if (v instanceof RawNumber) {
            sb.append(((RawNumber) v).text);
        } else {
            throw new IllegalStateException("Unexpected node type: " + v.getClass());
        }
    }

    /** JSON string escaping matching Python json.dumps(ensure_ascii=True). */
    private static void emitString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Wrapper preserving the original text of a non-integer number token. */
    private static final class RawNumber {
        final String text;
        RawNumber(String text) { this.text = text; }
    }

    // ---- Minimal recursive-descent JSON parser ----

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw err("Unexpected end of JSON");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': return parseLiteral("true", Boolean.TRUE);
                case 'f': return parseLiteral("false", Boolean.FALSE);
                case 'n': return parseLiteral("null", null);
                default:  return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            // TreeMap => keys sorted, mirroring sort_keys=True.
            TreeMap<String, Object> m = new TreeMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWs();
                if (peek() != '"') throw err("Expected string key");
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw err("Expected ',' or '}'");
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> l = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { pos++; return l; }
            while (true) {
                l.add(parseValue());
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw err("Expected ',' or ']'");
            }
            return l;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw err("Unterminated string");
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: throw err("Bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = pos;
            boolean isInt = true;
            if (peek() == '-') pos++;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') { pos++; }
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isInt = false; pos++; }
                else break;
            }
            String tok = s.substring(start, pos);
            if (tok.isEmpty() || tok.equals("-")) throw err("Invalid number");
            if (isInt) return new BigInteger(tok);
            return new RawNumber(tok);
        }

        private Object parseLiteral(String lit, Object val) {
            if (!s.startsWith(lit, pos)) throw err("Expected " + lit);
            pos += lit.length();
            return val;
        }

        private char peek() { skipWs(); return atEnd() ? '\0' : s.charAt(pos); }
        private char next() { return s.charAt(pos++); }
        private void expect(char c) {
            skipWs();
            if (atEnd() || s.charAt(pos) != c) throw err("Expected '" + c + "'");
            pos++;
        }
        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException(msg + " at index " + pos);
        }
    }
}
