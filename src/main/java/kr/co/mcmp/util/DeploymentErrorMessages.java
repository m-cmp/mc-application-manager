package kr.co.mcmp.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded diagnostics for authorized deployment logs, never a request/config dump. */
public final class DeploymentErrorMessages {
    private static final int MAX_LENGTH = 3072;
    private static final String SECRET_KEYS = "(?:(?:[a-z0-9]+[_-])*(?:password|passwd|pwd|root[_-]?password|"
            + "admin[_-]?password|user[_-]?(?:password|passwd)|passphrase|token|access[_-]?token|"
            + "refresh[_-]?token|id[_-]?token|client[_-]?secret|client[_-]?key(?:[_-]?data)?|"
            + "secret(?:[_-]?(?:access[_-]?)?key)?|secret[_-]?id|private[_-]?key(?:[_-]?data)?|access[_-]?key(?:[_-]?id)?|"
            + "api[_-]?key|api[_-]?token|auth[_-]?token|credentials?|authorization|"
            + "certificate[_-]authority[_-]data|client[_-]certificate[_-]data|"
            + "aws_access_key_id|aws_secret_access_key|session[_-]?token|x-amz-(?:signature|credential|security-token)))";
    private static final Pattern PEM = Pattern.compile(
            "(?is)-----BEGIN [A-Z0-9 ]*(?:PRIVATE KEY|CERTIFICATE)-----.*?(?:-----END [A-Z0-9 ]+-----|\\z)");
    private static final Pattern YAML_SECRET_BLOCK = Pattern.compile(
            "(?im)^(\\s*[\"']?" + SECRET_KEYS + "[\"']?\\s*:\\s*)[|>][-+]?[^\\r\\n]*\\R(?:[ \\t]+[^\\r\\n]*(?:\\R|$))*");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(?<![\\w-])([\"']?" + SECRET_KEYS + "[\"']?\\s*[:=]\\s*)"
                    + "(?:\"(?:\\\\.|[^\"\\\\])*(?:\"|$)|'[^']*(?:'|$)|[^\\s&,;}\\]<>\"']+)");
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern URL_USER = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/]+@");

    private DeploymentErrorMessages() { }

    public static String sanitize(String message) {
        return shorten(redact(message), MAX_LENGTH);
    }

    public static String describe(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> messages = new LinkedHashSet<>();
        for (Throwable cause = error; cause != null && visited.size() < 8 && visited.add(cause);
                cause = cause.getCause()) {
            String message = redact(cause.getMessage());
            if (!message.isBlank()) messages.add(shorten(message, 360));
        }
        return messages.isEmpty() ? "No additional failure details were provided."
                : String.join("; caused by: ", messages);
    }

    private static String redact(String message) {
        if (message == null) return "";
        String safe = PEM.matcher(message).replaceAll("[redacted PEM]");
        safe = YAML_SECRET_BLOCK.matcher(safe).replaceAll("$1[redacted]\n");
        safe = AUTH.matcher(safe).replaceAll("$1 [redacted]");
        safe = SECRET_VALUE.matcher(safe).replaceAll("$1[redacted]");
        safe = URL_USER.matcher(safe).replaceAll("$1[redacted]@");
        return safe.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
    }

    private static String shorten(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
    }
}
