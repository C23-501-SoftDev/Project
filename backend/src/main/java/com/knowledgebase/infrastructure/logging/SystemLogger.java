package com.knowledgebase.infrastructure.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public final class SystemLogger {

    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password",
            "token",
            "secret",
            "cookie",
            "authorization",
            "credential",
            "login",
            "email"
    );
    private static final String MASKED_VALUE = "[masked]";

    private final Logger logger;
    private final String component;

    private SystemLogger(Class<?> loggerType, String component) {
        this.logger = LoggerFactory.getLogger(loggerType);
        this.component = component;
    }

    public static SystemLogger getLogger(Class<?> loggerType, String component) {
        return new SystemLogger(loggerType, component);
    }

    public void info(String event, String action, String status, Object... fields) {
        if (logger.isInfoEnabled()) {
            logger.info(format("INFO", event, action, status, null, fields));
        }
    }

    public void warn(String event, String action, String status, Object... fields) {
        if (logger.isWarnEnabled()) {
            logger.warn(format("WARN", event, action, status, null, fields));
        }
    }

    public void debug(String event, String action, String status, Object... fields) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("DEBUG", event, action, status, null, fields));
        }
    }

    public void error(String event, String action, Throwable error, Object... fields) {
        if (logger.isErrorEnabled()) {
            logger.error(format("ERROR", event, action, "error", error, fields));
        }
    }

    public void error(String event, String action, String status, Object... fields) {
        if (logger.isErrorEnabled()) {
            logger.error(format("ERROR", event, action, status, null, fields));
        }
    }

    private String format(String level, String event, String action, String status, Throwable error, Object... fields) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("level", level);
        values.put("timestamp", Instant.now().toString());
        values.put("component", component);
        values.put("event", event);
        values.put("action", action);
        values.put("status", status);

        String requestId = MDC.get("request_id");
        if (requestId != null && !requestId.isBlank()) {
            values.put("request_id", requestId);
        }

        if (error != null) {
            values.put("error", error.getClass().getSimpleName());
        }

        appendFields(values, fields);

        if (isJsonFormat()) {
            return toJson(values);
        }
        return toKeyValue(values);
    }

    private void appendFields(Map<String, String> values, Object... fields) {
        if (fields == null) {
            return;
        }

        for (int index = 0; index + 1 < fields.length; index += 2) {
            Object rawKey = fields[index];
            if (rawKey == null) {
                continue;
            }

            String key = String.valueOf(rawKey);
            Object rawValue = fields[index + 1];
            values.put(key, sanitizeValue(key, rawValue));
        }
    }

    private String sanitizeValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return MASKED_VALUE;
        }
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private boolean isJsonFormat() {
        String format = System.getenv().getOrDefault("LOG_FORMAT", "text");
        return "json".equalsIgnoreCase(format);
    }

    private String toKeyValue(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(" ");
        values.forEach((key, value) -> joiner.add(key + "=" + quoteIfNeeded(value)));
        return joiner.toString();
    }

    private String quoteIfNeeded(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private String toJson(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        values.forEach((key, value) -> joiner.add("\"" + escapeJson(key) + "\":\"" + escapeJson(value) + "\""));
        return joiner.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
