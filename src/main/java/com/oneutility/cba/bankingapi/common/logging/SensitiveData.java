package com.oneutility.cba.bankingapi.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;

/**
 * Hides sensitive values before a request or response is logged. Fields are matched by name, in any case and ignoring
 * {@code _}, {@code -} and spaces, wherever they appear in the body:
 * <ul>
 * <li>identity numbers (BVN, NIN, and Fineract identifiers' {@code documentKey}, which holds them): last 4 digits kept;</li>
 * <li>secrets (passwords, client secrets, tokens, PINs, OTPs, API keys, {@code Authorization}): never logged.</li>
 * </ul>
 * Identity numbers that CBA-CORE repeats inside its error messages ("Client identifier of type BVN with value of ...")
 * are masked the same way. Everything else, account numbers, names, email, phone, address and date of birth included,
 * is logged as sent.
 */
public final class SensitiveData {

    public static final String REDACTED = "[REDACTED]";
    public static final int MAX_BODY_CHARS = 10_000;

    private static final Set<String> IDENTITY_NUMBERS = Set.of("bvn", "nin", "documentkey");
    private static final Set<String> SECRETS = Set.of("password", "newpassword", "oldpassword", "repeatpassword",
            "secret", "clientsecret", "token", "accesstoken", "refreshtoken", "idtoken", "pin", "otp", "apikey",
            "authorization", "proxyauthorization", "cookie", "setcookie", "clientassertion");

    // Fineract's duplicate-identifier message: "Client identifier of type BVN with value of 22200000001 already exists."
    private static final Pattern IDENTIFIER_IN_MESSAGE = Pattern.compile("(?i)(identifier of type .{1,60}? with value of )(\\S+?)(\\.? already exists|\\.|\\s|$)");

    private static final ObjectMapper JSON = new ObjectMapper();

    private SensitiveData() {
    }

    /** Whether a header's value must never be logged. */
    public static boolean isSecretHeader(String name) {
        return SECRETS.contains(normalise(name));
    }

    /**
     * The body as it may be logged: masked JSON or form data, cut off after {@value #MAX_BODY_CHARS} characters, or a
     * placeholder for anything else (files, binary).
     */
    public static String body(byte[] content, MediaType type) {
        if (content == null || content.length == 0) {
            return null;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        String masked;
        if (type != null && (MediaType.APPLICATION_JSON.isCompatibleWith(type) || type.getSubtype().endsWith("+json"))) {
            masked = json(text);
        } else if (type != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(type)) {
            masked = form(text);
        } else {
            return "[" + content.length + " bytes" + (type == null ? "" : " of " + type) + " not logged]";
        }
        return masked.length() <= MAX_BODY_CHARS ? masked
                : masked.substring(0, MAX_BODY_CHARS) + "...[" + (masked.length() - MAX_BODY_CHARS) + " more]";
    }

    /** A JSON document with sensitive fields masked. Text that is not valid JSON is not logged. */
    public static String json(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            if (node == null) {
                return null;
            }
            return JSON.writeValueAsString(mask(node));
        } catch (JsonProcessingException e) {
            return "[" + text.length() + " characters of invalid JSON not logged]";
        }
    }

    /** {@code a=1&b=2} data (form bodies and query strings) with sensitive parameters masked. */
    public static String form(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringJoiner out = new StringJoiner("&");
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.add(pair);
                continue;
            }
            String name = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            String shown = maskValue(name, value);
            out.add(pair.substring(0, eq) + "="
                    + (shown.equals(value) ? pair.substring(eq + 1) : URLEncoder.encode(shown, StandardCharsets.UTF_8)));
        }
        return out.toString();
    }

    /** A value as it may be logged, given the name of the field it is in. */
    public static String maskValue(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        String name = normalise(fieldName);
        if (SECRETS.contains(name)) {
            return REDACTED;
        }
        if (IDENTITY_NUMBERS.contains(name)) {
            return lastFour(value);
        }
        return value;
    }

    /** Masks identity numbers quoted inside a sentence, such as CBA-CORE's error messages. */
    static String maskInText(String text) {
        Matcher matcher = IDENTIFIER_IN_MESSAGE.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        do {
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    matcher.group(1) + lastFour(matcher.group(2)) + matcher.group(3)));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    static String lastFour(String value) {
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private static JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = normalise(field.getKey());
                JsonNode value = field.getValue();
                if (SECRETS.contains(name) && !value.isNull()) {
                    field.setValue(TextNode.valueOf(REDACTED));
                } else if (IDENTITY_NUMBERS.contains(name) && value.isValueNode() && !value.isNull()) {
                    field.setValue(TextNode.valueOf(lastFour(value.asText())));
                } else if (value.isTextual()) {
                    field.setValue(TextNode.valueOf(maskInText(value.asText())));
                } else {
                    mask(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(SensitiveData::mask);
        }
        return node;
    }

    private static String normalise(String name) {
        return name == null ? "" : name.replaceAll("[\\s_\\-]", "").toLowerCase(Locale.ROOT);
    }
}
