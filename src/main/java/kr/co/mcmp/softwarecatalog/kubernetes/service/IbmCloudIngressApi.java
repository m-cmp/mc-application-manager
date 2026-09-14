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
import java.util.*;
import org.springframework.stereotype.Component;

/** IBM's official ALB configuration API; no shell/CLI, redirect following or response-body logging. */
@Component
public class IbmCloudIngressApi {
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    public IbmCloudIngressApi() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build()); }
    IbmCloudIngressApi(HttpClient http) { this.http = http; }

    record Session(String token, URI containers, URI vpc) {
        @Override public String toString() { return "IBM session (credentials withheld)"; }
    }

    Session login(IbmIngressAutomationProperties.Profile profile) {
        if (profile.getRegion() == null || !profile.getRegion().matches("[a-z]{2}-[a-z]+"))
            throw new IllegalArgumentException("Configure the IBM region in the automation profile.");
        String key = IbmIngressAutomationProperties.readOperatorFile(profile.getApiKeyFile(), "IBM API key").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("The IBM automation API key is empty.");
        var response = request("POST", URI.create("https://iam.cloud.ibm.com/identity/token"), null,
                "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + encode(key), "application/x-www-form-urlencoded");
        String token = response.path("access_token").asText();
        if (token.isBlank()) throw new IllegalArgumentException("IBM IAM did not return an access token.");
        return new Session(token, URI.create("https://containers.cloud.ibm.com/global/"),
                URI.create("https://" + profile.getRegion() + ".iaas.cloud.ibm.com"));
    }

    JsonNode cluster(Session session, String id) {
        requireId(id);
        JsonNode result = get(session, session.containers.resolve("v2/getCluster?cluster=" + encode(id) + "&v1-compatible"));
        if (!id.equals(result.path("id").asText()) || !"vpc-gen2".equals(result.path("provider").asText()))
            throw new IllegalArgumentException("Selected IBM resource is not the expected VPC Kubernetes cluster.");
        return result;
    }

    JsonNode proxyConfig(Session session, String id, String type) {
        requireId(id);
        if (!Set.of("public","private").contains(type)) throw new IllegalArgumentException("Invalid IBM LB type.");
        var result = get(session, session.containers.resolve("ingress/v2/load-balancer/configuration?cluster=" + encode(id) + "&type=" + type));
        if (!result.path("proxyProtocol").path("enable").isBoolean())
            throw new IllegalArgumentException("IBM returned an unknown PROXY configuration. AM will not infer that it is disabled or change the LB.");
        return result;
    }

    void enableProxy(Session session, String id, List<String> cidrs) {
        requireId(id);
        if (cidrs.isEmpty()) throw new IllegalArgumentException("No trusted IBM LB subnet CIDRs were discovered.");
        cidrs.forEach(K8sIngressAccessService::validateCidr);
        try {
            String body = json.writeValueAsString(Map.of("cluster", id,
                    "proxyProtocol", Map.of("enable", true, "cidr", cidrs, "headerTimeout", 5)));
            request("PATCH", session.containers.resolve("ingress/v2/load-balancer/configuration"), session.token, body, "application/json");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot encode IBM LB configuration."); }
    }

    /** Match actual managed Service hostnames, not every LB in the account. Check IP capacity before changing anything. */
    List<String> trustedSubnets(Session session, JsonNode cluster, Set<String> hostnames, boolean checkCapacity) {
        if (hostnames.isEmpty()) throw new IllegalArgumentException("IBM managed LB hostnames are not ready.");
        var vpcs = new HashSet<String>(); cluster.path("vpcs").forEach(v -> vpcs.add(v.asText()));
        var found = new HashSet<String>();
        var subnets = new LinkedHashMap<String,Integer>();
        URI page = session.vpc.resolve("/v1/load_balancers?version=2024-07-09&generation=2&limit=100");
        Set<URI> visited = new HashSet<>();
        while (page != null) {
            if (visited.size() >= 100 || !visited.add(page) || !Objects.equals(page.getAuthority(), session.vpc.getAuthority())
                    || !"https".equals(page.getScheme()) || !"/v1/load_balancers".equals(page.getPath()))
                throw new IllegalArgumentException("Invalid IBM load balancer pagination response.");
            JsonNode response = get(session, page);
            for (var lb : response.path("load_balancers")) {
                String hostname = lb.path("hostname").asText();
                if (!hostnames.contains(hostname)) continue;
                if (!found.add(hostname)) throw new IllegalArgumentException("Ambiguous IBM managed LB hostname.");
                if (!"active".equals(lb.path("provisioning_status").asText()))
                    throw new IllegalArgumentException("An IBM managed LB is still changing. Wait before preparing Ingress.");
                if (!lb.path("subnets").isArray() || lb.path("subnets").isEmpty())
                    throw new IllegalArgumentException("IBM LB subnet information is missing.");
                for (var subnet : lb.path("subnets")) subnets.merge(requireId(subnet.path("id").asText()), 1, Integer::sum);
            }
            String next = response.path("next").path("href").asText();
            page = next.isBlank() ? null : URI.create(next);
        }
        if (!found.equals(hostnames)) throw new IllegalArgumentException("Cannot match all managed Ingress LBs in the configured IBM account/region.");
        var cidrs = new TreeSet<String>();
        for (var entry : subnets.entrySet()) {
            var subnet = get(session, session.vpc.resolve("/v1/subnets/" + entry.getKey() + "?version=2024-07-09&generation=2"));
            if (!vpcs.contains(subnet.path("vpc").path("id").asText()))
                throw new IllegalArgumentException("IBM managed LB subnet is outside the selected cluster VPC.");
            if (checkCapacity && subnet.path("available_ipv4_address_count").asInt(-1) < 2 * entry.getValue())
                throw new IllegalArgumentException("IBM LB recreation requires two free IPs per LB in each subnet. Insufficient subnet capacity.");
            cidrs.add(K8sIngressAccessService.validateCidr(subnet.path("ipv4_cidr_block").asText()));
        }
        return List.copyOf(cidrs);
    }

    private JsonNode get(Session s, URI uri) { return request("GET", uri, s.token, null, "application/json"); }
    JsonNode request(String method, URI uri, String token, String body, String contentType) {
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40)).header("Accept", "application/json");
            if (token != null) builder.header("Authorization", "Bearer " + token);
            builder.header("Content-Type", contentType).method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalArgumentException("IBM " + method + " request failed (HTTP " + response.statusCode() + "). Check automation account permissions and IBM service status.");
            return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
        } catch (IllegalArgumentException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("IBM request interrupted; check live configuration before retrying."); }
        catch (Exception e) { throw new IllegalArgumentException("IBM API request could not be completed. Check connectivity; a submitted change may still be running."); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static String requireId(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_-]{0,127}")) throw new IllegalArgumentException("Invalid IBM resource ID.");
        return value;
    }
}
