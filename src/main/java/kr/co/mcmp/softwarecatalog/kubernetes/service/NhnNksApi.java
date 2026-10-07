package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** NHN IaaS v2 token and NKS managed add-on API. Only Cinder installation is exposed. */
@Component
public class NhnNksApi {
    static final String ADDON = "cinder-csi-plugin";
    // NKS uses an underscore name in the catalog/install API, and a hyphen in cluster placeholders.
    static final String CATALOG_ADDON = "cinder_csi_plugin";
    private static boolean cinder(JsonNode addon) {
        return Set.of(ADDON, CATALOG_ADDON).contains(addon.path("name").asText());
    }
    record Session(String token, String tenantId, URI endpoint, Instant expires) {
        @Override public String toString() { return "NHN NKS session (credentials withheld)"; }
    }
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    public NhnNksApi() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    NhnNksApi(HttpClient http) { this.http = http; }

    Session login(NhnOpenBaoCredentials.Credentials credentials, String region) {
        URI identity;
        try {
            identity = URI.create(credentials.identityEndpoint());
            if (!officialHttps(identity) || !Set.of("api-identity-infrastructure.nhncloudservice.com",
                    "api-identity-infrastructure.cloud.toast.com").contains(identity.getHost().toLowerCase(Locale.ROOT))
                    || !Set.of("", "/", "/v2.0", "/v2.0/").contains(identity.getPath())) throw new IllegalArgumentException();
            identity = identity.resolve("/v2.0/tokens");
        } catch (Exception e) { throw invalidResponse("Configure the official NHN Identity endpoint in the registered credential."); }
        var response = request("POST", identity, null, Map.of("auth", Map.of("tenantId", credentials.tenantId(),
                "passwordCredentials", Map.of("username", credentials.username(), "password", credentials.password()))));
        try {
            var access = response.path("access");
            String token = access.path("token").path("id").asText();
            String tenant = access.path("token").path("tenant").path("id").asText();
            Instant expiry = Instant.parse(access.path("token").path("expires").asText());
            if (token.isBlank() || !credentials.tenantId().equals(tenant) || !expiry.isAfter(Instant.now().plusSeconds(30)))
                throw new IllegalArgumentException();
            Set<URI> endpoints = new HashSet<>();
            for (var service : access.path("serviceCatalog")) {
                if (!"container-infra".equals(service.path("type").asText())) continue;
                for (var endpoint : service.path("endpoints")) {
                    if (!region.equalsIgnoreCase(endpoint.path("region").asText())) continue;
                    URI uri = URI.create(endpoint.path("publicURL").asText());
                    if (!officialHttps(uri) || !(uri.getHost().toLowerCase(Locale.ROOT).endsWith(".nhncloudservice.com")
                            || uri.getHost().toLowerCase(Locale.ROOT).endsWith(".cloud.toast.com"))
                            || !Set.of("", "/", "/v1", "/v1/").contains(uri.getPath())) throw new IllegalArgumentException();
                    endpoints.add(uri.resolve("/v1/"));
                }
            }
            if (endpoints.size() != 1) throw new IllegalArgumentException();
            return new Session(token, tenant, endpoints.iterator().next(), expiry);
        } catch (Exception e) { throw invalidResponse("NHN did not return a valid project token and an unambiguous NKS endpoint for this region."); }
    }

    JsonNode cluster(Session session, String id) {
        requireUuid(id);
        var result = get(session, "clusters/" + id);
        if (!id.equals(result.path("uuid").asText()) || !session.tenantId().equals(result.path("project_id").asText()))
            throw new StorageOperationException(409, "NHN_CLUSTER_MISMATCH", "The NKS cluster does not match the selected cluster and registered NHN project.");
        return result;
    }

    Optional<JsonNode> installed(Session session, String id) {
        requireUuid(id);
        // The live NKS cluster-addons route rejects a trailing slash with 404.
        var result = get(session, "clusters/" + id + "/addons");
        if (!result.path("addons").isArray()) throw invalidResponse("NKS returned an invalid installed add-on list.");
        var matches = new ArrayList<JsonNode>();
        for (var addon : result.path("addons")) if (cinder(addon)) matches.add(addon);
        if (matches.size() > 1) throw invalidResponse("NKS returned an ambiguous Cinder add-on installation.");
        for (var addon : matches) {
            if (!id.equals(addon.path("cluster_uuid").asText()) || !session.tenantId().equals(addon.path("project_id").asText()))
                throw new StorageOperationException(409, "NHN_CLUSTER_MISMATCH", "The installed add-on belongs to a different NKS cluster or project.");
        }
        // NKS includes uninstalled add-on placeholders (version=null, status=NOT_INSTALLED).
        // Validate the target above, then allow these entries to proceed to a new installation.
        return matches.stream().filter(addon -> !"NOT_INSTALLED".equals(addon.path("status").asText())).findFirst();
    }

    String compatibleVersion(Session session, JsonNode cluster) {
        String version = cluster.path("coe_version").asText();
        String image = cluster.path("labels").path("node_image").asText();
        String platform = cluster.path("labels").path("platform_version").asText();
        if (version.isBlank() || image.isBlank() || platform.isBlank())
            throw new StorageOperationException(409, "NHN_ADDON_COMPATIBILITY_UNKNOWN", "NKS must provide the cluster Kubernetes version, base image and platform version before AM can select a compatible Cinder add-on.");
        var result = get(session, "addons/?k8s_version=" + encode(version) + "&image=" + encode(image) + "&platform_version=" + encode(platform));
        if (!result.path("addons").isArray()) throw invalidResponse("NKS returned an invalid compatible add-on list.");
        var versions = new ArrayList<String>();
        for (var addon : result.path("addons")) {
            String candidate = addon.path("version").asText();
            if (cinder(addon) && candidate.matches("v?\\d+(?:\\.\\d+){1,3}(?:-nks\\d+)?")) versions.add(candidate);
        }
        return versions.stream().max(NhnNksApi::compareVersions).orElseThrow(() ->
                new StorageOperationException(409, "NHN_ADDON_UNAVAILABLE", "NKS offers no supported Cinder add-on for this Kubernetes version, base image and platform. Check the NKS registry and cluster support."));
    }

    void install(Session session, String id, String version) {
        requireUuid(id);
        var result = request("POST", session.endpoint().resolve("clusters/" + id + "/addons"), session.token(),
                Map.of("name", CATALOG_ADDON, "version", version, "resolve_conflicts", "none"));
        if (!id.equals(result.path("uuid").asText())) throw invalidResponse("NKS did not confirm the selected cluster for the installation request. Refresh the add-on status before retrying.");
    }

    private JsonNode get(Session session, String path) {
        if (!session.expires().isAfter(Instant.now()))
            throw new StorageOperationException(502, "NHN_AUTHENTICATION_FAILED", "The NHN project token expired. Refresh the add-on status and retry.");
        return request("GET", session.endpoint().resolve(path), session.token(), null);
    }
    private JsonNode request(String method, URI uri, String token, Object body) {
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (token != null) builder.header("X-Auth-Token", token).header("OpenStack-API-Version", "container-infra latest");
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) throw new StorageOperationException(502, "NHN_AUTHENTICATION_FAILED", "NHN rejected the project credentials or token. Check the registered NHN API credential.");
            if (status == 403) throw new StorageOperationException(502, "NHN_ADDON_FORBIDDEN", "NHN denied the operation. Check the NHN account's NKS project permissions.");
            if (status == 409) throw new StorageOperationException(409, "NHN_ADDON_CONFLICT", "NKS reported an existing add-on or another cluster operation. Refresh status before retrying; AM will not overwrite it.");
            if (status == 400) throw new StorageOperationException(409, "NHN_ADDON_PRECONDITION", "NKS rejected the request. Check NKS registry activation, cluster readiness and add-on compatibility.");
            if (status < 200 || status >= 300 || response.body().length() > 1048576)
                throw invalidResponse("The NHN NKS API is unavailable. Check connectivity and refresh status before retrying an installation.");
            return json.readTree(response.body());
        } catch (StorageOperationException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw invalidResponse("NHN add-on verification was interrupted. Refresh status before retrying.");
        } catch (Exception e) { throw invalidResponse("The NHN NKS request could not be completed. Refresh status before retrying; a submitted installation may still be running."); }
    }
    private static boolean officialHttps(URI uri) {
        return "https".equals(uri.getScheme()) && uri.getHost() != null && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
    }
    static void requireUuid(String id) {
        if (id == null || !id.matches("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
            throw new StorageOperationException(400, "NHN_CLUSTER_ID_MISSING", "The selected cluster must have an NHN NKS cluster UUID.");
    }
    private static int compareVersions(String left, String right) {
        var pattern = java.util.regex.Pattern.compile("\\d+");
        var a = pattern.matcher(left); var b = pattern.matcher(right);
        while (a.find()) {
            if (!b.find()) return 1;
            int comparison = new java.math.BigInteger(a.group()).compareTo(new java.math.BigInteger(b.group()));
            if (comparison != 0) return comparison;
        }
        return b.find() ? -1 : left.compareTo(right);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static StorageOperationException invalidResponse(String message) { return new StorageOperationException(502, "NHN_NKS_API_UNAVAILABLE", message); }
}
