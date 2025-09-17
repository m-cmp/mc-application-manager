package kr.co.mcmp.ape.cbtumblebug.api;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.MciAccessInfoDto;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.SshKeyResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.ape.cbtumblebug.dto.VmSpecDto;
import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
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
        log.info("Executing command on MCI: {}, VM: {}", mciId, vmId);
        return executeWithConnectionCheck("executeMciCommand", () -> {
            try {
                String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/cmd/mci/%s", nsId, mciId));
                HttpHeaders headers = createCommonHeaders();

                // 요청 본문 생성
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("command", Collections.singletonList(command));

                // 쿼리 파라미터 추가
                if (subGroupId != null && !subGroupId.isEmpty()) {
                    apiUrl += "?subGroupId=" + subGroupId;
                }
                if (vmId != null && !vmId.isEmpty()) {
                    apiUrl += (apiUrl.contains("?") ? "&" : "?") + "vmId=" + vmId;
                }

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
                    log.info("command result: {}", response.getBody());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(responseBody);
                    JsonNode resultsNode = rootNode.path("results");
                    if (resultsNode.isArray() && resultsNode.size() > 0) {
                        JsonNode firstResult = resultsNode.get(0);
                        String stdout = firstResult.path("stdout").path("0").asText();
                        String stderr = firstResult.path("stderr").path("0").asText();
                        if (!stderr.isEmpty()) {
                            log.warn("Command execution produced stderr: {}", stderr);
                        }
                        return stdout;
                    } else {
                        throw new CbtumblebugException("Unexpected response format");
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
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci", namespace));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<MciResponse> response = restClient.request(apiUrl, headers, null, HttpMethod.GET,
                    new ParameterizedTypeReference<MciResponse>() {
                    });
            return response.getBody() != null ? response.getBody().getMci() : Collections.emptyList();
        });
    }

    public MciAccessInfoDto getSSHKeyForMci(String namespace, String mciId) {
        log.info("Fetching SSH Key for MCI: {} in namespace: {}", mciId, namespace);
        return executeWithConnectionCheck("getSSHKeyForMci", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci/%s", namespace, mciId));
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

    public MciDto getMciByMciId(String nsId, String mciId) {
        log.info("Fetching MCI by mciId: {}", mciId);
        return executeWithConnectionCheck("getMciByMciId", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci/%s", nsId, mciId));
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
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci/%s/vm/%s", nsId, mciId, vmId));
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

    public VmSpecDto lookupVmSpec(String connectionName, String vmSpecName) {
        log.info("Fetching VM Spec info for connection: {}, specName: {}", connectionName, vmSpecName);
        return executeWithConnectionCheck("lookupVmSpec", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/lookupSpec"));
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("connectionName", connectionName);
            requestMap.put("cspSpecName", vmSpecName);
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

}
