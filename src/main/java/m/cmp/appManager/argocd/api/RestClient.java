package m.cmp.appManager.argocd.api;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import m.cmp.appManager.argocd.exception.ArgocdException;
import m.cmp.appManager.util.AES256Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RestClient {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    public UriComponentsBuilder getUriBuilder(String httpUrl, String... pathSeg) {
    	return UriComponentsBuilder.fromHttpUrl(httpUrl).pathSegment(pathSeg);
    }

	public HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        if ( StringUtils.isNotBlank(token) ) {
        	String plainTextToken = AES256Util.decrypt(token);
        	
        	headers.setBearerAuth(plainTextToken);
        }
		return headers;
	}
    
	public <T, U> Object requestWithTokenAuth(String token, String url, HttpMethod method, U body, Class<T> clazz) {
		ResponseEntity<JsonNode> response = request(url, method, getHeaders(token), body, JsonNode.class);
		if (response.getBody() != null && !response.getBody().isEmpty()) {
			return getResponseBody(response.getBody(), clazz);
		} 
		return null;
	}
	
	public <T, U> Object requestWithoutToken(String url, HttpMethod method, U body, Class<T> clazz) {
		ResponseEntity<JsonNode> response = request(url, method, getHeaders(null), body, JsonNode.class);
		if (response.getBody() != null && !response.getBody().isEmpty()) {
			return getResponseBody(response.getBody(), clazz);
		} 
		return null;
	}
	
    public <T, U> ResponseEntity<T> request(String url, HttpMethod method, HttpHeaders headers, U body, Class<T> clazz) {
    	
    	log.info("[request]{} {}", method, url);
        ResponseEntity<T> response = null;
        
        try {
        
        	ObjectMapper mapper = new ObjectMapper();
    		JsonNode jsonBody = mapper.convertValue(body, JsonNode.class);
        	
    		HttpEntity<JsonNode> requestEntity = new HttpEntity<>(jsonBody, headers);
			//log.debug("[request]{} {} body={}", method, url, (jsonBody != null) ? jsonBody.toString(): "");
    	
			RestTemplate restTemplate = this.getSkipSslCertificateVerficationRestTemplate();
            response = restTemplate.exchange(url, method, requestEntity, clazz);
            
        } catch (HttpStatusCodeException e) {
            log.warn("[request]{} {}", method, url);
            log.warn("[response]{}", e.getResponseBodyAsString());
            log.warn(e.getMessage(), e);
            throw new ArgocdException(e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("[request]{} {}", method, url);
            log.warn(e.getMessage(), e);
        	throw new ArgocdException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        
        return response;

    }
    
    /**
     * ResponseBody clazz 타입으로 변환  
     */
    private <T> Object getResponseBody(JsonNode jsonNode, Class<T> clazz) {
		ObjectMapper mapper = new ObjectMapper();

		try {
			log.debug("{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		T responseBody = mapper.convertValue(jsonNode, new TypeReference<T>(){
		    @Override
		    public Type getType() {
		        return clazz;
		    }
		});
		
        return responseBody;
      }

    
    /**
     * SSL 인증서 무시
     * @return
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    private RestTemplate getSkipSslCertificateVerficationRestTemplate() {
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
        SSLContext sslContext = null;
        try {
            sslContext = org.apache.http.ssl.SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        } catch (KeyManagementException e) {
            log.warn(e.getMessage(), e);
            throw new ArgocdException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.warn(e.getMessage(), e);
            throw new ArgocdException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (KeyStoreException e) {
            log.warn(e.getMessage(), e);
            throw new ArgocdException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        //SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier()); //add 기존 기능 주석처리 기능 이상시 주석 해제 예정
        // disableRedirectHandling() => HttpStatus 302인 경우 Redirect 방지
        //CloseableHttpClient httpClient = HttpClientBuilder.create().setSSLSocketFactory(csf).disableRedirectHandling().build(); //add 기존 기능 주석처리 기능 이상시 주석 해제 예정
        //add
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .disableRedirectHandling()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        requestFactory.setHttpClient(httpClient);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        return restTemplate;
    }
    

}

	