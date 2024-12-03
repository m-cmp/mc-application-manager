package kr.co.mcmp.service.oss.repository.nexus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.config.oss.RestTemplateProvider;
import kr.co.mcmp.dto.oss.repository.CommonFormatType;
import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.exception.NexusClientException;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssServiceImpl;
import kr.co.mcmp.util.Base64Util;
import kr.co.mcmp.util.Base64Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Service
public class NexusRepositoryAdapterClient {

    private static final String GET_REPO_LIST = "/service/rest/v1/repositories";
    private static final String GET_REPO_BY_NAME = "/service/rest/v1/repositories/{repositoryName}";
    private static final String GET_REPO_DETAIL = "/service/rest/v1/repositories/{format}/{type}/{repositoryName}";
    private static final String POST_REPO_CREATE = "/service/rest/v1/repositories/{format}/{type}";
    private static final String PUT_REPO_UPDATE = "/service/rest/v1/repositories/{format}/{type}/{repositoryName}";
    private static final String DELETE_REPO_DELETE = "/service/rest/v1/repositories/{repositoryName}";

    private String nexusId = "";
    private String nexusPwd = "";
    private String baseUrl = "";
    private String authorization = "Authorization";

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

    public List<CommonRepository.RepositoryDto> getRepositoryList() {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(GET_REPO_LIST)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        return exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<List<CommonRepository.RepositoryDto>>() {});
    }

    public CommonRepository.RepositoryDto getRepositoryByName(String name) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(GET_REPO_BY_NAME)
                .buildAndExpand(name)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        return exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<CommonRepository.RepositoryDto>() {});
    }

    public CommonRepository.RepositoryDto getRepositoryDetailByName(CommonFormatType formatType, String name) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(GET_REPO_DETAIL)
                .buildAndExpand(formatType.getFormat(), formatType.getType(), name)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        return exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<CommonRepository.RepositoryDto>() {});
    }

    public Object createRepository(CommonRepository.RepositoryDto repositoryDto) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(POST_REPO_CREATE)
                .buildAndExpand(repositoryDto.getFormat(), repositoryDto.getType())
                .toUriString();

        HttpEntity<CommonRepository.RepositoryDto> request = getRequest(repositoryDto);
        return exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<Object>() {});
    }

    public Object updateRepository(CommonRepository.RepositoryDto repositoryDto) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(PUT_REPO_UPDATE)
                .buildAndExpand(repositoryDto.getFormat(), repositoryDto.getType(), repositoryDto.getName())
                .toUriString();

        HttpEntity<CommonRepository.RepositoryDto> request = getRequest(repositoryDto);
        return exchange(url, HttpMethod.PUT, request, new ParameterizedTypeReference<Object>() {});
    }

    public Object deleteRepository(String name) {
        getOssInfo();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(DELETE_REPO_DELETE)
                .buildAndExpand(name)
                .toUriString();

        HttpEntity<Void> request = getRequest(null);
        return exchange(url, HttpMethod.DELETE, request, new ParameterizedTypeReference<Object>() {});
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

    private String createToken() {
        String auth = nexusId + ":" + nexusPwd;
        return "Basic " + Base64Util.base64Encoding(auth);
    }

    private <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        RestTemplate template = RestTemplateProvider.get();
        try {
            System.out.println("url :" + url);
            ResponseEntity<T> response = template.exchange(url, method, requestEntity, responseType);
            System.out.println(response.getStatusCode());
            System.out.println("response : " + response.getBody());
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
