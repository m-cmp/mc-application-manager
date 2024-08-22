package kr.co.mcmp.service.oss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.config.oss.RestTemplateProvider;
import kr.co.mcmp.dto.oss.NexusFormatType;
import kr.co.mcmp.dto.oss.NexusRepositoryDto;
import kr.co.mcmp.exception.NexusClientException;
import kr.co.mcmp.util.Base64Util;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Log4j2
@Service
public class NexusRepositoryAdapterClient {

    private static final String NEXUS_ID = "admin";
    private static final String NEXUS_PWD = "tjxjfkxh!23";

    private static final String BASE_URL = "http://210.217.178.130:8081/service/rest";
    private static final String AUTHORIZATION = "Authorization";

    private static final String GET_REPO_LIST = "/v1/repositories";
    private static final String GET_REPO_ONE = "/v1/repositories/{name}";
    private static final String GET_REPO_BY_NAME = "/v1/repositories/{format}/{type}/{name}";
    private static final String POST_CREATE_REPO = "/v1/repositories/{format}/{type}";

    public List<NexusRepositoryDto.ResGetRepositoryDto> getRepositoryList() {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(GET_REPO_LIST)
                .toUriString();

        HttpEntity request = getRequest(null);
        RestTemplate template = RestTemplateProvider.get();

        try {
            ResponseEntity<List<NexusRepositoryDto.ResGetRepositoryDto>> response = template.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<List<NexusRepositoryDto.ResGetRepositoryDto>>() {});

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new IllegalArgumentException("Unexpected Response Status: " + response.getStatusCode() + " from URL: " + url);
            }

        } catch (HttpClientErrorException e) {
            String errorMessage = e.getResponseBodyAsString();
            String parseMessage = parseErrorMessage(errorMessage);
            throw new IllegalArgumentException(parseMessage, e);
        }
    }

    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryOne(String name) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(GET_REPO_ONE)
                .buildAndExpand(name)
                .toUriString();

        HttpEntity request = getRequest(null);
        RestTemplate template = RestTemplateProvider.get();

        try {
            ResponseEntity<NexusRepositoryDto.ResGetRepositoryDto> response = template.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<NexusRepositoryDto.ResGetRepositoryDto>() {});

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new NexusClientException("Unexpected Response Status: " + response.getStatusCode() + " from URL: " + url);
            }

        } catch (HttpClientErrorException e) {
            String errorMessage = e.getResponseBodyAsString();
            String parseMessage = parseErrorMessage(errorMessage);
            throw new NexusClientException(parseMessage);
        }
    }

    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryByName(NexusFormatType formatType, String name) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(GET_REPO_BY_NAME)
                .buildAndExpand(formatType.getFormat(), formatType.getType(), name)
                .toUriString();

        HttpEntity request = getRequest(null);
        RestTemplate template = RestTemplateProvider.get();

        try {
            ResponseEntity<NexusRepositoryDto.ResGetRepositoryDto> response = template.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<NexusRepositoryDto.ResGetRepositoryDto>() {});

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new NexusClientException("Unexpected Response Status: " + response.getStatusCode() + " from URL: " + url);
            }

        } catch (HttpClientErrorException e) {
            String errorMessage = e.getResponseBodyAsString();
            String parseMessage = parseErrorMessage(errorMessage);
            throw new NexusClientException(parseMessage);
        }
    }

    public Object createRepository(NexusFormatType formatType, NexusRepositoryDto.ReqCreateRepositoryDto repositoryDto) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(POST_CREATE_REPO)
                .buildAndExpand(formatType.getFormat(), formatType.getType())
                .toUriString();

        HttpEntity request = getRequest(repositoryDto);
        RestTemplate template = RestTemplateProvider.get();

        try {
            ResponseEntity<Object> response = template.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Object>() {});

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new IllegalArgumentException("Unexpected Response Status: " + response.getStatusCode() + " from URL: " + url);
            }

        } catch (HttpClientErrorException e) {
            String errorMessage = e.getResponseBodyAsString();
            String parseMessage = parseErrorMessage(errorMessage);
            throw new IllegalArgumentException(parseMessage, e);
        }
    }

    private static <T> HttpEntity getRequest(T body) {
        String basicToken = createToken();
        HttpHeaders headers = getHeaders(basicToken);
        if (body == null) {
            return new HttpEntity(headers);
        }
        return new HttpEntity(body, headers);
    }

    private static HttpHeaders getHeaders(String basicToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(AUTHORIZATION, basicToken);
        return headers;
    }

    private static String createToken() {
        String auth = NEXUS_ID + ":" + NEXUS_PWD;
        String encodedAuth = Base64Util.base64Encoding(auth);
        return "Basic " + encodedAuth;
    }

    private String parseErrorMessage(String errorMessage) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonPart = errorMessage.substring(errorMessage.indexOf("{"));

            JsonNode rootNode = mapper.readTree(jsonPart);
            JsonNode messageNode = rootNode.path("message");

            String message = messageNode.asText();
            return message.replace("\\\"", "\"").replace("\"", "");

        } catch (Exception e) {
            return "Message Parsing Error";
        }
    }
}
