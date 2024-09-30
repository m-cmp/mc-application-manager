package kr.co.mcmp.ape.cbtumblebug.api;


import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbtumblebugRestClient {
    
    private final RestTemplate restTemplate;

    public <T> ResponseEntity<T> request(String apiUrl, HttpHeaders headers, Object body, HttpMethod httpMethod, ParameterizedTypeReference<T> responseType) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
            HttpEntity<?> entity = new HttpEntity<>(body, headers);

            return restTemplate.exchange(builder.toUriString(), httpMethod, entity, responseType);
        } catch (HttpStatusCodeException e) {
            log.error("HTTP error: {} {}", e.getRawStatusCode(), e.getStatusText());
            log.error("Response body: {}", e.getResponseBodyAsString());
            throw new CbtumblebugException(e.getRawStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("RestClientException: ", e);
            throw new CbtumblebugException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error occurred");
        } catch (Exception e) {
            log.error("Unexpected error: ", e);
            throw new CbtumblebugException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error occurred");
        }
    }
}
