package kr.co.mcmp.service.oss.component.nexus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.config.oss.RestTemplateProvider;
import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.exception.NexusClientException;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssServiceImpl;
import kr.co.mcmp.util.Base64Util;
import kr.co.mcmp.util.Base64Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class NexusComponentAdapterClient {

    private static final String GET_COMP_LIST = "/service/rest/v1/components";
    private static final String GET_COMP_DETAIL = "/service/rest/v1/components/{id}";
    private static final String DELETE_COMP_DELETE = "/service/rest/v1/components/{id}";
    private static final String POST_COMP_CREATE = "/service/rest/v1/components";

    private String nexusId = "";
    private String nexusPwd = "";
    private String baseUrl = "";
    private String authorization = "Authorization";

    @Autowired private ObjectMapper mapper;
    @Autowired private OssServiceImpl ossService;

    private void getOssInfo() {
        try {
            OssDto nexus = ossService.detailOssByOssNameIgnoreCase("NEXUS");
            this.nexusId = nexus.getOssUsername();
            this.nexusPwd = Base64Utils.base64Decoding(nexus.getOssPassword());
            this.baseUrl = nexus.getOssUrl();
        } catch (Exception e) {
            throw new IllegalArgumentException ("DB Nexus 계정 정보가 없습니다.");
        }
    }

    public List<CommonComponent.ComponentDto> getComponentList(String name) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(GET_COMP_LIST)
                .queryParam("repository", name)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        Map<String, Object> response = exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
        return mapper.convertValue(response.get("items"), new TypeReference<List<CommonComponent.ComponentDto>>() {});
    }

    public CommonComponent.ComponentDto getComponentDetailByName(String id) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(GET_COMP_DETAIL)
                .buildAndExpand(id)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        Map<String, Object> response = exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {});
        return mapper.convertValue(response, new TypeReference<CommonComponent.ComponentDto>() {});
    }

    public Object deleteComponent(String id) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(DELETE_COMP_DELETE)
                .buildAndExpand(id)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        return exchange(url, HttpMethod.DELETE, request, new ParameterizedTypeReference<Object>() {});
    }

    public Object createComponent(String name, MultiValueMap<String, Object> uploadMap) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(POST_COMP_CREATE)
                .queryParam("repository", name)
                .toUriString();

        HttpEntity<MultiValueMap<String, Object>> request = getUploadRequest(uploadMap);
        return exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<Object>() {});
    }

    private <T> HttpEntity<T> getRequest(T body) {
        String basicToken = createToken();
        HttpHeaders headers = getHeaders(basicToken);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders getHeaders(String basicToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(authorization, basicToken);
        return headers;
    }

    private <T> HttpEntity<T> getUploadRequest(T body) {
        String basicToken = createToken();
        HttpHeaders headers = getUploadHeaders(basicToken);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders getUploadHeaders(String basicToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(authorization, basicToken);
        return headers;
    }

    private String createToken() {
        String auth = nexusId + ":" + nexusPwd;
        return "Basic " + Base64Util.base64Encoding(auth);
    }

    private <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        RestTemplate template = RestTemplateProvider.get();
        try {
            ResponseEntity<T> response = template.exchange(url, method, requestEntity, responseType);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            String errorMessage = e.getResponseBodyAsString();
            throw new NexusClientException(parseErrorMessage(errorMessage));
        }
    }

    private String parseErrorMessage(String errorMessage) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(errorMessage);
            JsonNode messageNode = rootNode.path("message");
            return messageNode.asText().replace("\\\"", "\"").replace("\"", "");
        } catch (Exception e) {
            return "Message Parsing Error";
        }
    }
}
