package kr.co.mcmp.softwarecatalog.application.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Registry endpoint and read-only Engine preflight shared by image import paths. */
final class DockerRegistryAccess {
    record Endpoint(String authority, String host, int port, boolean secure) {}

    static Endpoint endpoint(String override, String nexusUrl, int port, boolean secure) {
        boolean explicit = override != null && !override.isBlank();
        URI uri = URI.create(explicit ? override.trim() : nexusUrl);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (explicit && uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Registry must be an HTTP(S) host and port without credentials or a path.");
        }
        boolean tls = explicit ? "https".equalsIgnoreCase(scheme) : secure;
        int registryPort = explicit ? (uri.getPort() < 0 ? (tls ? 443 : 80) : uri.getPort()) : port;
        if (registryPort < 1 || registryPort > 65535) throw new IllegalArgumentException("Invalid registry port.");
        String host = uri.getHost();
        String authority = host + ":" + registryPort;
        return new Endpoint(authority, host, registryPort, tls);
    }

    static boolean isHttpAllowed(String authority) {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "info", "--format", "{{json .RegistryConfig}}")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) return false;
            return isHttpAllowed(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), authority);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    static boolean isHttpAllowed(String config, String authority) throws java.io.IOException {
        JsonNode root = new ObjectMapper().readTree(config);
        if (root == null) return false;
        JsonNode index = root.path("IndexConfigs").path(authority);
        if (index.path("Secure").isBoolean() && !index.path("Secure").booleanValue()) return true;
        String host = URI.create("http://" + authority).getHost();
        if ("localhost".equalsIgnoreCase(host)) host = "127.0.0.1";
        if (host == null || !host.matches("[0-9]+(?:\\.[0-9]+){3}")) return false;
        for (JsonNode cidr : root.path("InsecureRegistryCIDRs")) {
            if (inIpv4Subnet(host, cidr.asText())) return true;
        }
        return false;
    }

    private static boolean inIpv4Subnet(String host, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            int bits = Integer.parseInt(parts[1]);
            if (bits < 0 || bits > 32) return false;
            long mask = bits == 0 ? 0 : (0xffffffffL << (32 - bits)) & 0xffffffffL;
            return (ipv4(host) & mask) == (ipv4(parts[0]) & mask);
        } catch (IllegalArgumentException e) { return false; }
    }

    private static long ipv4(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) throw new IllegalArgumentException("Invalid IPv4");
        long value = 0;
        for (String octet : octets) {
            int part = Integer.parseInt(octet);
            if (part < 0 || part > 255) throw new IllegalArgumentException("Invalid IPv4");
            value = (value << 8) | part;
        }
        return value;
    }
}
