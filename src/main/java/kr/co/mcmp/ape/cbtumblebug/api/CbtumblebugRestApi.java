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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceResponse;
import kr.co.mcmp.ape.cbtumblebug.dto.Spec;
import kr.co.mcmp.ape.cbtumblebug.dto.VmDto;
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

    public boolean checkTumblebug() {
        log.info("Checking if Tumblebug is ready");
        String apiUrl = createApiUrl("/tumblebug/readyz");
        HttpHeaders headers = createCommonHeaders();
        try {
            ResponseEntity<String> response = restClient.request(apiUrl, headers, null, HttpMethod.GET, new ParameterizedTypeReference<String>() {});
            log.info("Tumblebug readyz response: {}", response.getBody());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Tumblebug connection failed", e);
            return false;
        }
    }
    
    private <T> T executeWithConnectionCheck(String operationName, Supplier<T> apiCall) {
        if (!checkTumblebug()) {
            log.error("Tumblebug에 연결할 수 없습니다. {} 작업을 수행할 수 없습니다.", operationName);
            throw new CbtumblebugException("Tumblebug 연결 실패");
        }
        return apiCall.get();
    }

    public List<NamespaceDto> getAllNamespace() {
        log.info("Fetching all namespaces");
        return executeWithConnectionCheck("getAllNamespace", () ->{
            String apiUrl = createApiUrl("/tumblebug/ns");
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<NamespaceResponse> response = restClient.request(apiUrl, headers, null, HttpMethod.GET, new ParameterizedTypeReference<NamespaceResponse>() {});
            return response.getBody() != null ? response.getBody().getNs() : Collections.emptyList();
        });
    }

    public List<MciDto> getMcisByNamespace(String namespace) {
        log.info("Fetching MCIs by namespace: {}", namespace);
        return executeWithConnectionCheck("getMcisByNamespace", () ->{
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci", namespace));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<MciResponse> response = restClient.request(apiUrl, headers, null, HttpMethod.GET, new ParameterizedTypeReference<MciResponse>() {});
            return response.getBody() != null ? response.getBody().getMci() : Collections.emptyList();
        });
    }

    // public String getK8sClusterInfo(){
    //     log.info("Fetching all K8sClusterInfo");
    //     return executeWithConnectionCheck("getK8sClusterInfo", () ->{
    //         String apiUrl = createApiUrl("/k8sClusterInfo");
    //         HttpHeaders headers = createCommonHeaders();
    //         ResponseEntity<String> response = restClient.request(apiUrl, headers, headers, HttpMethod.GET, new ParameterizedTypeReference<String>() {});
    //         return response.getBody() != null ? response.getBody() : null;
    //     });
    // }

    public List<K8sClusterDto> getAllK8sClusters(String namespace){
        log.info("Fetching K8s Clusters by namespace: {}", namespace);
        return executeWithConnectionCheck("getK8sClustersByNamespace", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8scluster", namespace));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<K8sClusterResponse> response = restClient.request(
                apiUrl, 
                headers, 
                null, 
                HttpMethod.GET, 
                new ParameterizedTypeReference<K8sClusterResponse>() {}
            );
            return response.getBody() != null ? response.getBody().getK8sClusterInfo() : Collections.emptyList();
        });
    }

    public K8sClusterDto getK8sClusterByName(String namespace, String clusterName){
        log.info("Fetching K8s Cluster by name: {} in namespace: {}", clusterName, namespace);
        return executeWithConnectionCheck("getK8sClusterByName", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/k8scluster/%s", namespace, clusterName));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<K8sClusterDto> response = restClient.request(
                apiUrl, 
                headers, 
                null, 
                HttpMethod.GET, 
                new ParameterizedTypeReference<K8sClusterDto>() {}
            );
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
                new ParameterizedTypeReference<MciDto>() {}
            );
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
                new ParameterizedTypeReference<Spec>() {}
            );
            return response.getBody();
        });
    }

    public VmDto getVmInfo(String nsId, String mciId, String vmId) {
        log.info("Fetching VM info for VM ID: {}", vmId);
        return executeWithConnectionCheck("getVmInfo", () -> {
            String apiUrl = createApiUrl(String.format("/tumblebug/ns/%s/mci/%s/vm/%s", nsId, mciId, vmId));
            HttpHeaders headers = createCommonHeaders();
            ResponseEntity<VmDto> response = restClient.request(
                apiUrl,
                headers,
                null,
                HttpMethod.GET,
                new ParameterizedTypeReference<VmDto>() {}
            );
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
                new ParameterizedTypeReference<K8sSpec>() {}
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Error response from server: {}", response.getBody());
                throw new CbtumblebugException("Failed to fetch spec info: " + response.getStatusCode());
            }
    
            return response.getBody();
        });
        // String apiUrl = "http://localhost:1323/tumblebug/lookupSpec";
        // String requestBody = String.format("{\"connectionName\": \"%s\", \"cspResourceId\": \"%s\"}", connectionName, cspResourceId);

        // HttpHeaders headers = createCommonHeaders();
        // ResponseEntity<K8sSpec> response = restClient.request(
        //     apiUrl,
        //     headers,
        //     requestBody,
        //     HttpMethod.POST,
        //     new ParameterizedTypeReference<K8sSpec>() {}
        // );

        // return response.getBody();
    }
}
