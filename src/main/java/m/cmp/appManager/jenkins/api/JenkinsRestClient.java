package m.cmp.appManager.jenkins.api;

import com.cdancy.jenkins.rest.domain.crumb.Crumb;
import m.cmp.appManager.jenkins.exception.JenkinsException;
import m.cmp.appManager.util.AES256Util;
import m.cmp.appManager.util.Base64Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.Charset;

@Slf4j
@Component
public class JenkinsRestClient {
    /**
     * Basic 인증 방식 API 호출
     * @param apiUrl
     * @param username, password
     * @param httpMethod
     * @param body
     * @param clazz
     * @param <T>
     * @param <U>
     * @return
     */
    public <T, U> ResponseEntity<T> requestByBasicAuth(String apiUrl, String username, String password, HttpMethod httpMethod, U body, Class<T> clazz) {
    	String plainTextPassword = AES256Util.decrypt(password);
    	
        // header 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        headers.setBasicAuth(Base64Utils.base64Encoding(username+":"+plainTextPassword));
    	
    	return request(apiUrl, headers, body, httpMethod, clazz);
    }

    /**
     * jenkins API 호출
     * @param apiUrl
     * @param username, password
     * @param httpMethod
     * @param body
     * @param clazz
     * @param <T>
     * @param <U>
     * @return
     */
    public <T, U> ResponseEntity<T> requestJenkinsCrumbAPI(String apiUrl, String username, String password, Crumb crumb, HttpMethod httpMethod, U body, Class<T> clazz) {
    	String plainTextPassword = AES256Util.decrypt(password);
    	
        // header 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", Charset.forName("UTF-8")));
        headers.setBasicAuth(Base64Utils.base64Encoding(username+":"+plainTextPassword));
        headers.set("jenkins-crumb", crumb.value());
        headers.set("Cookie", crumb.sessionIdCookie());

        return request(apiUrl, headers, body, httpMethod, clazz);
    }

    /**
     * API 호출 (공통)
     * @param apiUrl
     * @param httpMethod
     * @param clazz
     * @param <T>
     * @return
     */
    public <T, U> ResponseEntity<T> request(String apiUrl, HttpHeaders headers, U body, HttpMethod httpMethod, Class<T> clazz) {
        ResponseEntity<T> response = null;
        try {
        	UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(apiUrl).build();
        	HttpEntity<U> entity = new HttpEntity<U>(body, headers);
        	
        	RestTemplate restTemplate = new RestTemplate();
            response = restTemplate.exchange(uriComponents.toString(), httpMethod, entity, clazz);
        } catch (RestClientResponseException e) {
            log.error("[callAPI] RestClientResponseException ", e);
            log.error("## Response All : {} ", e.getMessage());
            log.error("## Response Code :  {} ", e.getRawStatusCode());
            log.error("## Response StatusMessage : {} ", e.getStatusText());
            throw new RestClientResponseException(e.getMessage(), e.getRawStatusCode(), e.getStatusText(), null, null, null);
        } catch (Exception e) {
            log.error("[callAPI] Exception ", e);
            throw new JenkinsException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
        }
        log.debug("[callAPI] Success");
        return response;
    }

}
