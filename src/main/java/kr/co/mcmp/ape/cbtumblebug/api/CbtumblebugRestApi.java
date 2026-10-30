package kr.co.mcmp.ape.cbtumblebug.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sNodeGroupAutoscaleRequest;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.ObjectStorageInfo;
import kr.co.mcmp.ape.cbtumblebug.dto.ObjectStorageListObjectsResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.ObjectStorageListResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.ObjectStoragePresignedUrlResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.MciAccessInfoDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciCommandResult;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.SshKeyResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.ape.cbtumblebug.dto.VmSpecDto;
import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
import kr.co.mcmp.util.DeploymentErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbtumblebugRestApi {

    @Value("${cbtumblebug.url}")
    private String cbtumblebugUrl;

    @Value("${cbtumblebug.port}")
    private String cbtumblebugPort;

    @Value("${cbtumblebug.id}")
    private String cbtumblebugId;

    @Value("${cbtumblebug.pass}")
    private String cbtumblebugPass;

    private final CbtumblebugRestClient restClient;

    private boolean isTumblebugReady = false;
    private long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 60000; // 1 minute

    private HttpHeaders createCommonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = cbtumblebugId + ":" + cbtumblebugPass;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private String createApiUrl(String endpoint) {
        String apiUrl = String.format("http://%s:%s%s", cbtumblebugUrl, cbtumblebugPort, endpoint);
        log.info("apiUrl : {}", apiUrl);

        return apiUrl;
    }
    /*
     * public boolean checkTumblebug() {
     * log.info("Checking if Tumblebug is ready");
     * String apiUrl = createApiUrl("/tumblebug/readyz");
     * HttpHeaders headers = createCommonHeaders();
     * try {
     * ResponseEntity<String> response = restClient.request(apiUrl, headers, null,
     * HttpMethod.GET, new ParameterizedTypeReference<String>() {});
     * log.info("Tumblebug readyz response: {}", response.getBody());
     * return response.getStatusCode().is2xxSuccessful();
     * } catch (Exception e) {
     * log.error("Tumblebug connection failed", e);
     * return false;
     * }
     * }
     * 
     * 
     * private <T> T executeWithConnectionCheck(String operationName, Supplier<T>
     * apiCall) {
     * if (!checkTumblebug()) {
     * log.error("Tumblebug에 연결할 수 없습니다. {} 작업을 수행할 수 없습니다.", operationName);
     * throw new CbtumblebugException("Tumblebug 연결 실패");
     * }
     * return apiCall.get();
     * }
     */

    public boolean checkTumblebug() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime > CHECK_INTERVAL || !isTumblebugReady) {
            log.info("Checking if Tumblebug is ready");
            String apiUrl = createApiUrl("/tumblebug/readyz");
            HttpHeaders headers = createCommonHeaders();
            try {
                ResponseEntity<String> response = restClient.request(apiUrl, headers, null, HttpMethod.GET,
                        new ParameterizedTypeReference<String>() {
                        });
                isTumblebugReady = response.getStatusCode().is2xxSuccessful();
                lastCheckTime = currentTime;
            } catch (Exception e) {
                log.error("Tumblebug connection failed", e);
                isTumblebugReady = false;
            }
        }
        return isTumblebugReady;
    }

    public <T> T executeWithConnectionCheck(String operationName, Supplier<T> apiCall) {
        if (!checkTumblebug()) {
            throw new CbtumblebugException("Tumblebug is not ready");
        }
        return apiCall.get();
    }

    public String executeMciCommand(String nsId, String mciId, String command, String subGroupId, String vmId) {
        return executeMciCommandResult(nsId, mciId, command, subGroupId, vmId).stdout();
    }

    /**
     * Executes a command on exactly one VM and preserves both stdout and stderr.
     * Docker-over-SSH uses this method so a failed command cannot be mistaken for
     * a successful command merely because the HTTP request itself returned 2xx.
     */
    public MciCommandResult executeMciCommandResult(
            String nsId,
            String mciId,
            String command,
            String subGroupId,
            String vmId) {
        log.info("Executing command on MCI: {}, VM: {}", mciId, vmId);
        return executeWithConnectionCheck("executeMciCommand", () -> {
            try {
                String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/cmd/infra/%s", nsId, mciId));
                HttpHeaders headers = createCommonHeaders();

                // 요청 본문 생성
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("command", Collections.singletonList(command));

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(apiUrl);
                if (subGroupId != null && !subGroupId.isEmpty()) {
                    uriBuilder.queryParam("subGroupId", subGroupId);
                }
                if (vmId != null && !vmId.isEmpty()) {
                    // CB-Tumblebug targets one VM with nodeId. subGroupId targets
                    // every VM in a node group, while vmId is ignored by 0.13.x.
                    uriBuilder.queryParam("nodeId", vmId);
                }
                apiUrl = uriBuilder.build().encode().toUriString();

                String jsonBody = new ObjectMapper().writeValueAsString(requestBody);

                ResponseEntity<String> response = restClient.request(
                        apiUrl,
                        headers,
                        jsonBody,
                        HttpMethod.POST,
                        new ParameterizedTypeReference<String>() {
                        });

                if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    if (responseBody == null || responseBody.isBlank()) {
                        throw new CbtumblebugException("Empty response from MCI command execution");
                    }
                    log.debug("MCI command response received: {} bytes", responseBody.length());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(responseBody);
                    JsonNode resultsNode = rootNode.path("results");
                    if (resultsNode.isArray() && resultsNode.size() == 1) {
                        JsonNode firstResult = resultsNode.get(0);
                        String stdout = extractCommandOutput(firstResult.path("stdout"));
                        String stderr = extractCommandOutput(firstResult.path("stderr"));
                        // Preserve transport errors without changing stdout/exit-marker
                        // handling: error can also describe an ordinary nonzero exit.
                        String remoteError = extractCommandOutput(firstResult.path("error"));
                        if (!remoteError.isBlank()) {
                            if (!stderr.contains(remoteError)) {
                                stderr += (stderr.isBlank() ? "" : "\n")
                                        + "Remote command error on VM " + vmId + ": " + remoteError;
                            }
                            stderr = DeploymentErrorMessages.sanitize(stderr);
                        }
                        
                        log.debug("Parsed stdout: '{}', stderr: '{}'", stdout, stderr);
                        
                        if (!stderr.isEmpty()) {
                            log.warn("Command execution produced stderr: {}", stderr);
                        }
                        return new MciCommandResult(stdout, stderr);
                    } else {
                        throw new CbtumblebugException(
                                "Expected one VM command result but received "
                                        + (resultsNode.isArray() ? resultsNode.size() : 0));
                    }
                } else {
                    throw new CbtumblebugException(
                            "MCI command execution failed. Status code: " + response.getStatusCodeValue());
                }

            } catch (JsonProcessingException e) {
                log.error("Error serializing request body", e);
                throw new CbtumblebugException("Failed to serialize request body: " + e.getMessage());
            } catch (RestClientException e) {
                log.error("Error executing command on MCI", e);
                throw new CbtumblebugException("Failed to execute command on MCI: " + e.getMessage());
            }
        });
    }

    private String extractCommandOutput(JsonNode outputNode) {
        if (outputNode == null || outputNode.isMissingNode() || outputNode.isNull()) {
            return "";
        }
        if (outputNode.isTextual() || outputNode.isNumber() || outputNode.isBoolean()) {
            return outputNode.asText();
        }

        StringBuilder output = new StringBuilder();
        if (outputNode.isArray()) {
            outputNode.forEach(value -> appendCommandOutput(output, value));
        } else if (outputNode.isObject()) {
            outputNode.elements().forEachRemaining(value -> appendCommandOutput(output, value));
        }
        return output.toString();
    }

    private void appendCommandOutput(StringBuilder output, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
        output.append(value.isValueNode() ? value.asText() : value.toString());
    }

    public List<NamespaceDto> getAllNamespace() {
        log.info("Fetching all namespaces");
        return executeWithConnectionCheck("getAllNamespace", () -> {
            String apiUrl = createApiUrl("/tumblebug/ns");
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<NamespaceResponse> response = restClient.request(apiUrl, headers, null, HttpMethod.GET,
                    new ParameterizedTypeReference<NamespaceResponse>() {
                    });
            return response.getBody() != null ? response.getBody().getNs() : Collections.emptyList();
        });
    }

    public List<MciDto> getMcisByNamespace(String namespace) {
        log.info("Fetching MCIs by namespace: {}", namespace);
        return executeWithConnectionCheck("getMcisByNamespace", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/infra", namespace));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<MciResponse> response = restClient.request(apiUrl, headers, null, HttpMethod.GET,
                    new ParameterizedTypeReference<MciResponse>() {
                    });
            return response.getBody() != null ? response.getBody().getInfra() : Collections.emptyList();
        });
    }

    public MciAccessInfoDto getSSHKeyForMci(String namespace, String mciId) {
        log.info("Fetching SSH Key for MCI: {} in namespace: {}", mciId, namespace);
        return executeWithConnectionCheck("getSSHKeyForMci", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/infra/%s", namespace, mciId));
            HttpHeaders headers = createCommonHeaders();

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("option", "accessinfo")
                    .queryParam("accessInfoOption", "showSshKey");

            ResponseEntity<MciAccessInfoDto> response = restClient.request(
                    builder.toUriString(),
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<MciAccessInfoDto>() {
                    });

            return response.getBody();
        });
    }

    public List<K8sClusterDto> getAllK8sClusters(String namespace) {
        log.info("Fetching K8s Clusters by namespace: {}", namespace);
        return executeWithConnectionCheck("getK8sClustersByNamespace", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8sCluster", namespace));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<K8sClusterResponse> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<K8sClusterResponse>() {
                    });
            return response.getBody() != null ? response.getBody().getK8sClusterInfo() : Collections.emptyList();
        });
    }

    public K8sClusterDto getK8sClusterByName(String namespace, String clusterName) {
        log.info("Fetching K8s Cluster by name: {} in namespace: {}", clusterName, namespace);
        return executeWithConnectionCheck("getK8sClusterByName", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8sCluster/%s", namespace, clusterName));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<K8sClusterDto> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<K8sClusterDto>() {
                    });
            return response.getBody();
        });
    }

    public String getK8sClusterKubeconfig(String namespace, String clusterName) {
        log.info("Fetching K8s Cluster kubeconfig by name: {} in namespace: {}", clusterName, namespace);
        return executeWithConnectionCheck("getK8sClusterKubeconfig", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8sCluster/%s/kubeconfig", namespace, clusterName));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<JsonNode> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<JsonNode>() {
                    });
            JsonNode body = response.getBody();
            String kubeconfig = body != null ? body.path("kubeconfig").asText(null) : null;
            if (kubeconfig == null || kubeconfig.trim().isEmpty()) {
                throw new CbtumblebugException("Tumblebug kubeconfig response is empty");
            }
            return kubeconfig;
        });
    }

    public String getK8sClusterToken(String namespace, String clusterName) {
        log.info("Fetching K8s Cluster token by name: {} in namespace: {}", clusterName, namespace);
        return executeWithConnectionCheck("getK8sClusterToken", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8sCluster/%s/token", namespace, clusterName));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<JsonNode> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<JsonNode>() {
                    });
            JsonNode body = response.getBody();
            String token = body != null ? body.path("execCredential").path("status").path("token").asText(null) : null;
            if (token == null || token.trim().isEmpty()) {
                throw new CbtumblebugException("Tumblebug token response is empty");
            }
            return token;
        });
    }

    public MciDto getMciByMciId(String nsId, String mciId) {
        log.info("Fetching MCI by mciId: {}", mciId);
        return executeWithConnectionCheck("getMciByMciId", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/infra/%s", nsId, mciId));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<MciDto> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<MciDto>() {
                    });
            return response.getBody();
        });
    }

    public Spec getSpecBySpecId(String nsId, String specId) {
        log.info("Fetching Spec by specId: {}", specId);
        return executeWithConnectionCheck("getSpecBySpecId", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/resources/spec/%s", nsId, specId));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<Spec> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<Spec>() {
                    });
            log.info("Spec : {}", response.getBody().toString());
            return response.getBody();
        });
    }

    public VmAccessInfo getVmInfo(String nsId, String mciId, String vmId) {
        log.info("Fetching VM info for VM ID: {}", vmId);
        return executeWithConnectionCheck("getVmInfo", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/infra/%s/node/%s", nsId, mciId, vmId));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<VmAccessInfo> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<VmAccessInfo>() {
                    });
            return response.getBody();
        });
    }

    /** Read CSP attachment metadata through Tumblebug; no CSP credentials in AM. */
    public JsonNode getCspWorkerInfo(String connectionName, String cspId) {
        // Azure VMSS workers use ARM resource IDs containing '/'. Keep the whole
        // ID in one encoded path segment; the URI overload avoids double encoding.
        boolean simpleId = cspId != null && cspId.matches("[A-Za-z0-9._:-]+");
        boolean azureId = cspId != null && cspId.matches(
                "(?i)/subscriptions/[a-z0-9-]+/resourceGroups/[a-z0-9._()-]+/providers/Microsoft\\.Compute/"
                + "(?:virtualMachines/[a-z0-9._-]+|virtualMachineScaleSets/[a-z0-9._-]+/virtualMachines/[0-9]+)");
        if ((!simpleId && !azureId) || java.util.Arrays.stream(cspId.split("/"))
                .anyMatch(segment -> segment.equals(".") || segment.equals("..")))
            throw new CbtumblebugException("Unsupported worker CSP identifier for attachment lookup");
        return securityMetadataRequest("/tumblebug/forward/cspvm/" + UriUtils.encodePathSegment(cspId, StandardCharsets.UTF_8), HttpMethod.POST,
                Map.of("ConnectionName", connectionName));
    }

    public JsonNode listSecurityGroupMetadata(String nsId) {
        return securityMetadataRequest("/tumblebug/ns/" + nsId + "/resources/securityGroup", HttpMethod.GET, null);
    }

    public JsonNode getVNetSecurityMetadata(String nsId, String vnetId) {
        return securityMetadataRequest("/tumblebug/ns/" + nsId + "/resources/vNet/" + vnetId, HttpMethod.GET, null);
    }

    public JsonNode registerExistingSecurityGroup(String nsId, String connectionName, String vNetId,
                                                  String name, String cspId) {
        return securityMetadataRequest("/tumblebug/ns/" + nsId + "/resources/securityGroup?option=register",
                HttpMethod.POST, Map.of("name", name, "connectionName", connectionName,
                        "vNetId", vNetId, "cspResourceId", cspId));
    }

    private JsonNode securityMetadataRequest(String path, HttpMethod method, Map<String,String> body) {
        return executeWithConnectionCheck("securityMetadata", () -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                var response = restClient.request(URI.create(createApiUrl(path)), createCommonHeaders(),
                        body == null ? null : mapper.writeValueAsString(body), method,
                        new ParameterizedTypeReference<String>() {});
                JsonNode result = mapper.readTree(response.getBody());
                if (!response.getStatusCode().is2xxSuccessful() || result == null || !result.isObject())
                    throw new CbtumblebugException("Invalid security metadata response from Tumblebug");
                return result;
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new CbtumblebugException("Could not parse security metadata from Tumblebug");
            }
        });
    }

    public K8sSpec lookupSpec(String connectionName, String cspResourceId) {
        log.info("Fetching Spec info for K8s ID : {}", connectionName);
        return executeWithConnectionCheck("lookupSpec", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/lookupSpec"));
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("connectionName", connectionName);
            requestMap.put("cspResourceId", cspResourceId);
            String requestBody = "";
            try {
                requestBody = objectMapper.writeValueAsString(requestMap);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage());
            }
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<K8sSpec> response = restClient.request(
                    apiUrl,
                    headers,
                    requestBody,
                    HttpMethod.POST,
                    new ParameterizedTypeReference<K8sSpec>() {
                    });
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Error response from server: {}", response.getBody());
                throw new CbtumblebugException("Failed to fetch spec info: " + response.getStatusCode());
            }

            return response.getBody();
        });
    }

    /**
     * Adds one restricted inbound TCP rule without resubmitting the Security
     * Group's existing rules. In particular, existing ALL protocol rules must
     * not be copied into this request because CB-Tumblebug's full update path
     * can reject them during port validation.
     */
    public String addInboundTcpFirewallRule(
            String nsId,
            String securityGroupId,
            int port,
            String cidr) {
        return executeWithConnectionCheck("addInboundTcpFirewallRule", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/securityGroup/%s/rules",
                    nsId,
                    securityGroupId));
            HttpHeaders headers = createCommonHeaders();

            Map<String, Object> firewallRule = new HashMap<>();
            firewallRule.put("Direction", "inbound");
            firewallRule.put("Protocol", "TCP");
            firewallRule.put("CIDR", cidr);
            firewallRule.put("Ports", String.valueOf(port));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("firewallRules", List.of(firewallRule));

            try {
                String jsonBody = new ObjectMapper().writeValueAsString(requestBody);
                ResponseEntity<String> response = restClient.request(
                        apiUrl,
                        headers,
                        jsonBody,
                        HttpMethod.POST,
                        new ParameterizedTypeReference<String>() {
                        });
                return requireSuccessfulFirewallRuleMutation(response.getBody(), "add");
            } catch (JsonProcessingException e) {
                throw new CbtumblebugException("Failed to serialize firewall rule request: " + e.getMessage());
            }
        });
    }

    /**
     * Checks whether the exact inbound TCP rule already exists. Application
     * Manager uses this before adding a rule so an operator-owned rule is never
     * mistaken for an application-owned rule during uninstall.
     */
    public boolean hasInboundTcpFirewallRule(
            String nsId,
            String securityGroupId,
            int port,
            String cidr) {
        return executeWithConnectionCheck("hasInboundTcpFirewallRule", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/securityGroup/%s",
                    nsId,
                    securityGroupId));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<String> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<String>() {
                    });

            try {
                JsonNode root = new ObjectMapper().readTree(response.getBody());
                JsonNode rules = root.path("firewallRules");
                if (!rules.isArray()) {
                    throw new CbtumblebugException("Security Group response does not contain firewallRules");
                }

                String expectedPort = String.valueOf(port);
                for (JsonNode rule : rules) {
                    String actualPort = rule.hasNonNull("Port")
                            ? rule.path("Port").asText()
                            : rule.path("Ports").asText();
                    if ("inbound".equalsIgnoreCase(rule.path("Direction").asText())
                            && "TCP".equalsIgnoreCase(rule.path("Protocol").asText())
                            && cidr.equals(rule.path("CIDR").asText())
                            && expectedPort.equals(actualPort)) {
                        return true;
                    }
                }
                return false;
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new CbtumblebugException("Failed to inspect Security Group rules: " + e.getMessage());
            }
        });
    }

    /**
     * Deletes only the exact inbound TCP rule. This endpoint preserves every
     * other rule, including provider-created ALL protocol rules.
     */
    public String deleteInboundTcpFirewallRule(
            String nsId,
            String securityGroupId,
            int port,
            String cidr) {
        return executeWithConnectionCheck("deleteInboundTcpFirewallRule", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/securityGroup/%s/rules",
                    nsId,
                    securityGroupId));
            HttpHeaders headers = createCommonHeaders();

            Map<String, Object> firewallRule = new HashMap<>();
            firewallRule.put("Direction", "inbound");
            firewallRule.put("Protocol", "TCP");
            firewallRule.put("CIDR", cidr);
            firewallRule.put("Ports", String.valueOf(port));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("firewallRules", List.of(firewallRule));

            try {
                String jsonBody = new ObjectMapper().writeValueAsString(requestBody);
                ResponseEntity<String> response = restClient.request(
                        apiUrl,
                        headers,
                        jsonBody,
                        HttpMethod.DELETE,
                        new ParameterizedTypeReference<String>() {
                        });
                return requireSuccessfulFirewallRuleMutation(response.getBody(), "delete");
            } catch (JsonProcessingException e) {
                throw new CbtumblebugException("Failed to serialize firewall rule deletion request: " + e.getMessage());
            }
        });
    }

    private String requireSuccessfulFirewallRuleMutation(String responseBody, String operation) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new CbtumblebugException("Tumblebug returned an empty response while trying to "
                    + operation + " a firewall rule");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(responseBody);
            if (root.has("success") && !root.path("success").asBoolean()) {
                String message = root.path("message").asText("unknown error");
                throw new CbtumblebugException("Tumblebug failed to " + operation
                        + " the firewall rule: " + message);
            }
            return responseBody;
        } catch (JsonProcessingException e) {
            throw new CbtumblebugException("Failed to parse firewall rule response: " + e.getMessage());
        }
    }

    public VmSpecDto lookupVmSpec(String connectionName, String vmSpecName) {
        log.info("Fetching VM Spec info for connection: {}, specName: {}", connectionName, vmSpecName);
        return executeWithConnectionCheck("lookupVmSpec", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/lookupSpec"));
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("connectionName", connectionName);
            requestMap.put("cspSpecName", vmSpecName);
            // Support both the documented field and older/forked servers that still bind cspResourceId.
            requestMap.put("cspResourceId", vmSpecName);
            String requestBody = "";
            try {
                requestBody = objectMapper.writeValueAsString(requestMap);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage());
            }
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<VmSpecDto> response = restClient.request(
                    apiUrl,
                    headers,
                    (Object) requestBody,
                    HttpMethod.POST,
                    new ParameterizedTypeReference<VmSpecDto>() {
                    });
            return response.getBody();
        });
    }

    public SshKeyResponse.SshKeyInfo getSshKey(String nsId, String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Invalid VM SSH key ID");
        }
        return executeWithConnectionCheck("getSshKey", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/resources/sshKey/%s", nsId, keyId));
            return restClient.request(apiUrl, createCommonHeaders(), null, HttpMethod.GET,
                    new ParameterizedTypeReference<SshKeyResponse.SshKeyInfo>() { }).getBody();
        });
    }

    public List<SshKeyResponse.SshKeyInfo> getAllSshKeys(String nsId) {
        log.info("Fetching all SSH Keys in namespace: {}", nsId);
        return executeWithConnectionCheck("getAllSshKeys", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/resources/sshKey", nsId));
            HttpHeaders headers = createCommonHeaders();

            ResponseEntity<SshKeyResponse> response = restClient.request(
                    apiUrl,
                    headers,
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<SshKeyResponse>() {
                    });

            return response.getBody() != null ? response.getBody().getSshKey() : Collections.emptyList();
        });
    }

    public List<ObjectStorageInfo> getObjectStorages(String nsId) {
        log.info("Fetching Object Storage resources in namespace: {}", nsId);
        return executeWithConnectionCheck("getObjectStorages", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/objectStorage",
                    encodePathSegment(nsId)));
            ResponseEntity<ObjectStorageListResponse> response = restClient.request(
                    apiUrl,
                    createCommonHeaders(),
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<ObjectStorageListResponse>() {
                    });
            return response.getBody() != null
                    ? response.getBody().getObjectStorage()
                    : Collections.emptyList();
        });
    }

    public ObjectStorageInfo getObjectStorage(String nsId, String objectStorageId) {
        log.info("Fetching Object Storage resource: namespace={}, id={}", nsId, objectStorageId);
        return executeWithConnectionCheck("getObjectStorage", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/objectStorage/%s",
                    encodePathSegment(nsId),
                    encodePathSegment(objectStorageId)));
            ResponseEntity<ObjectStorageInfo> response = restClient.request(
                    apiUrl,
                    createCommonHeaders(),
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<ObjectStorageInfo>() {
                    });
            return response.getBody();
        });
    }

    public ObjectStorageListObjectsResponse listObjectStorageObjects(
            String nsId,
            String objectStorageId,
            String credentialHolder) {
        log.info("Listing objects: namespace={}, objectStorageId={}", nsId, objectStorageId);
        return executeWithConnectionCheck("listObjectStorageObjects", () -> {
            String apiUrl = createApiUrl(String.format(
                    "/tumblebug/ns/%s/resources/objectStorage/%s/object",
                    encodePathSegment(nsId),
                    encodePathSegment(objectStorageId)));
            ResponseEntity<ObjectStorageListObjectsResponse> response = restClient.request(
                    apiUrl,
                    createObjectStorageHeaders(credentialHolder),
                    null,
                    HttpMethod.GET,
                    new ParameterizedTypeReference<ObjectStorageListObjectsResponse>() {
                    });
            return response.getBody() != null
                    ? response.getBody()
                    : ObjectStorageListObjectsResponse.builder().build();
        });
    }

    public ObjectStoragePresignedUrlResponse generateObjectStoragePresignedUrl(
            String nsId,
            String objectStorageId,
            String objectKey,
            String operation,
            int expiresSeconds,
            String credentialHolder) {
        log.info(
                "Generating Object Storage presigned URL: namespace={}, objectStorageId={}, operation={}",
                nsId,
                objectStorageId,
                operation);
        return executeWithConnectionCheck("generateObjectStoragePresignedUrl", () -> {
            URI apiUri = UriComponentsBuilder.fromHttpUrl(createApiUrl("/tumblebug"))
                    .pathSegment(
                            "ns",
                            nsId,
                            "resources",
                            "objectStorage",
                            objectStorageId,
                            "object",
                            objectKey,
                            "presignedUrl")
                    .queryParam("operation", operation)
                    .queryParam("expires", expiresSeconds)
                    .build()
                    .encode()
                    .toUri();
            ResponseEntity<ObjectStoragePresignedUrlResponse> response = restClient.request(
                    apiUri,
                    createObjectStorageHeaders(credentialHolder),
                    null,
                    HttpMethod.POST,
                    new ParameterizedTypeReference<ObjectStoragePresignedUrlResponse>() {
                    });
            if (response.getBody() == null) {
                throw new CbtumblebugException("Tumblebug returned an empty presigned URL response");
            }
            return response.getBody();
        });
    }

    private HttpHeaders createObjectStorageHeaders(String credentialHolder) {
        HttpHeaders headers = createCommonHeaders();
        if (credentialHolder != null && !credentialHolder.isBlank()) {
            headers.set("x-credential-holder", credentialHolder);
        }
        return headers;
    }

    private String encodePathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tumblebug path parameter is required");
        }
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    /**
     * CSP 리소스 ID로 SSH Key 조회
     *
     * @param nsId          네임스페이스 ID
     * @param cspResourceId CSP 리소스 ID
     * @return 해당 CSP 리소스 ID와 매칭되는 SSH Key 정보 (없으면 null)
     */
    public SshKeyResponse.SshKeyInfo getSshKeyByCspResourceId(String nsId, String cspResourceId) {
        log.info("Fetching SSH Key in namespace '{}' with cspResourceId='{}'", nsId, cspResourceId);
        // filterKey API 파라미터로 지원되지 않으므로, 전체 조회 후 필터링
        List<SshKeyResponse.SshKeyInfo> allKeys = getAllSshKeys(nsId);
        if (allKeys == null || allKeys.isEmpty()) {
            return null;
        }
        return allKeys.stream()
                    .filter(key -> cspResourceId.equals(key.getCspResourceId()))
                    .findFirst()
                    .orElse(null);
    }

    /*
     * Disabled because there is currently no call site in AM and the corresponding
     * CB-Tumblebug K8s command endpoint is not used by the active Helm deployment flow.
     * The implementation is retained here for reference until its compatibility and
     * future usage are decided.
     *
    public String executeK8sClusterCommand(String nsId, String k8sClusterId, String k8sClusterNamespace,
            String k8sClusterPodName, String k8sClusterContainerName, String command) {
        log.info("Executing command on K8s Cluster: {}, Pod: {}, Container: {}", k8sClusterId, k8sClusterPodName,
                k8sClusterContainerName);
        return executeWithConnectionCheck("executeK8sClusterCommand", () -> {
            try {
                String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/cmd/k8sCluster/%s", nsId, k8sClusterId));
                HttpHeaders headers = createCommonHeaders();

                // 요청 본문 생성
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("command", Collections.singletonList(command));

                // 쿼리 파라미터 추가 (UriComponentsBuilder 사용)
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                        .queryParam("k8sClusterNamespace", k8sClusterNamespace)
                        .queryParam("k8sClusterPodName", k8sClusterPodName);

                // 컨테이너 이름이 제공된 경우에만 추가
                if (k8sClusterContainerName != null && !k8sClusterContainerName.isEmpty()) {
                    builder.queryParam("k8sClusterContainerName", k8sClusterContainerName);
                }

                String jsonBody = new ObjectMapper().writeValueAsString(requestBody);

                ResponseEntity<String> response = restClient.request(
                        builder.toUriString(),
                        headers,
                        jsonBody,
                        HttpMethod.POST,
                        new ParameterizedTypeReference<String>() {
                        });

                if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    log.info("K8s cluster command result: {}", responseBody);

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(responseBody);
                    JsonNode resultsNode = rootNode.path("results");

                    if (resultsNode.isArray() && resultsNode.size() > 0) {
                        JsonNode firstResult = resultsNode.get(0);
                        String stdout = firstResult.path("stdout").asText();
                        String stderr = firstResult.path("stderr").asText();

                        if (!stderr.isEmpty()) {
                            log.warn("K8s cluster command execution produced stderr: {}", stderr);
                        }

                        return stdout;
                    } else {
                        throw new CbtumblebugException("Unexpected response format from K8s cluster command");
                    }
                } else {
                    throw new CbtumblebugException(
                            "K8s cluster command execution failed. Status code: " + response.getStatusCodeValue());
                }

            } catch (JsonProcessingException e) {
                log.error("Error serializing request body for K8s cluster command", e);
                throw new CbtumblebugException("Failed to serialize request body: " + e.getMessage());
            } catch (RestClientException e) {
                log.error("Error executing command on K8s cluster", e);
                throw new CbtumblebugException("Failed to execute command on K8s cluster: " + e.getMessage());
            }
        });
    }
    */

    /**
     * K8S 노드 그룹의 오토스케일 크기를 변경합니다.
     */
    public String changeK8sNodeGroupAutoscaleSize(String nsId, String k8sClusterId, String k8sNodeGroupName, 
                                                 K8sNodeGroupAutoscaleRequest request) {
            log.info("Changing K8S node group autoscale size: nsId={}, clusterId={}, nodeGroup={}, request={}", 
                    nsId, k8sClusterId, k8sNodeGroupName, request);
            
            return executeWithConnectionCheck("changeK8sNodeGroupAutoscaleSize", () -> {
            try {
                String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8sCluster/%s/k8sNodeGroup/%s/autoscaleSize", 
                        nsId, k8sClusterId, k8sNodeGroupName));
                HttpHeaders headers = createCommonHeaders();
                
                ObjectMapper mapper = new ObjectMapper();
                mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
                String jsonBody = mapper.writeValueAsString(request);
                log.info("Request body: {}", jsonBody);
                
                ResponseEntity<String> response = restClient.request(
                        apiUrl,
                        headers,
                        jsonBody,
                        HttpMethod.PUT,
                        new ParameterizedTypeReference<String>() {}
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully changed K8S node group autoscale size");
                    return response.getBody();
                } else {
                    throw new CbtumblebugException(
                            "Failed to change K8S node group autoscale size. Status code: " + response.getStatusCodeValue());
                }
                
            } catch (JsonProcessingException e) {
                log.error("Error serializing request body for K8S node group autoscale", e);
                throw new CbtumblebugException("Failed to serialize request body: " + e.getMessage());
            } catch (RestClientException e) {
                log.error("Error changing K8S node group autoscale size", e);
                throw new CbtumblebugException("Failed to change K8S node group autoscale size: " + e.getMessage());
            }
        });
    }
    

}
