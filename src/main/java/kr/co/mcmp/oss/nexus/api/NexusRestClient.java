package kr.co.mcmp.oss.nexus.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.nexus.exception.NexusException;
import kr.co.mcmp.util.AES256Util;
import kr.co.mcmp.util.Base64Util;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Slf4j
@Component
public class NexusRestClient {

	private static String NEXUS_API_BASE_URL = "/service/rest/";
    
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    public UriComponentsBuilder getUriBuilder(String url, String path) {
    	return UriComponentsBuilder.fromHttpUrl(url).pathSegment(NEXUS_API_BASE_URL, path);
    }
    
    public HttpHeaders getHeaderByPassword(OssDto nexus) throws Exception {
    	if ( StringUtils.isBlank(nexus.getOssUsername()) ) {
    		throw new NexusException(HttpStatus.BAD_REQUEST, "nexus username is not found");
    	}
    	if ( StringUtils.isBlank(nexus.getOssPassword()) ) {
    		throw new NexusException(HttpStatus.BAD_REQUEST, "nexus user password is not found");
    	}

		String plainTextPassword = AES256Util.decrypt(nexus.getOssPassword());
    	
    	String value = nexus.getOssUsername() + ":" + plainTextPassword;
    	String encodedValue = Base64Util.base64Encoding(value);
    	log.info("basicAuth >>> Basic {}", encodedValue);
    	
    	HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
    	headers.set("Authorization", "Basic "+encodedValue);
    	
    	return headers;
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
            log.error(e.getMessage(), e);
            throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage(), e);
            throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
        } catch (KeyStoreException e) {
            log.error(e.getMessage(), e);
            throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
        }

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

    private <U> HttpEntity<U> getHttpEntity(U body, HttpHeaders headers, String uri){
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = null;
        if(body != null){
            try {
                requestBody = objectMapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                log.error("request body JsonProcessingException", e);
                throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Request body JsonProcessingException", uri);
            }
        }
        HttpEntity<U> entity = new HttpEntity<U>((U) requestBody, headers);
        return entity;
    }

    public <T, U> Object request(OssDto nexus, String apiUri, HttpMethod httpMethod, U body, Class<T> clazz)  {
    	T responseBody = null;

        try {
	        HttpHeaders headers = getHeaderByPassword(nexus);

	        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(apiUri).build();
	        HttpEntity<U> entity = new HttpEntity<>(body, headers);
	        
	        RestTemplate restTemplate = this.getSkipSslCertificateVerficationRestTemplate();
	        ResponseEntity<T> response = restTemplate.exchange(uriComponents.toString(), httpMethod, entity, clazz);
			if ( response.getBody() != null ) {
        		ObjectMapper objectMapper = new ObjectMapper();
				objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				
				responseBody = objectMapper.convertValue(response.getBody(), new TypeReference<T>(){
				    @Override
				    public Type getType() {
				        return clazz;
				    }
				});
			} 
        } catch ( HttpClientErrorException e) {
            log.error("[requestNexusAPI] ## Message = " + e.getMessage(), e);
            log.error("[requestNexusAPI] ## Response Code = " + e.getStatusCode().value());
            log.error("[requestNexusAPI] ## Response Body = " + e.getResponseBodyAsString());
            throw new NexusException(e.getStatusCode().value(), e.getMessage(), apiUri);
        } catch ( RestClientException e ) {
            log.error("요청하신 URL("+apiUri+")은 유효하지 않습니다.");
            if ( e.getRootCause() instanceof SocketTimeoutException) {
                log.error("SocketTimeoutException", e);
                throw new NexusException(HttpStatus.REQUEST_TIMEOUT.value(), "The URL you requested is not valid.", apiUri);
            } else if ( e.getRootCause() instanceof ConnectTimeoutException) {
                log.error("ConnectTimeoutException", e);
                throw new NexusException(HttpStatus.REQUEST_TIMEOUT.value(), "The URL you requested is not valid.", apiUri);
            } else {
                log.error(e.getMessage(), e);
                throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), apiUri);
            }
        } catch (Exception ex) {
            log.error("요청하신 URL("+apiUri+") 처리 중 에러가 발생했습니다.");
            log.error(ex.getMessage(), ex);
            throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An error occurred while processing the requested URL.", apiUri);
        }

        return responseBody;
    }
    
    /**
     * Nexus URL 연결 체크(200은 인스턴스가 읽기 요청을 처리할 수 있음을 나타내고 503은 그렇지 않은 경우)
     * @param apiUri
     * @param httpMethod
     * @return
     */
    public HttpStatus checkNexusConnection(String apiUri, HttpMethod httpMethod)  {
    	HttpHeaders headers = new HttpHeaders();
    	headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
    	
    	UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(apiUri).build();
    	HttpEntity<?> entity = this.getHttpEntity(null, headers, apiUri);
    	
    	RestTemplate restTemplate = this.getSkipSslCertificateVerficationRestTemplate();
    	ResponseEntity<Object> response = null;
    	try {
    		response = restTemplate.exchange(uriComponents.toString(), httpMethod, entity, Object.class);
    		if ( response != null ) {
    			return response.getStatusCode();
    		}
    		else {
    			return null;
    		}
    	} catch ( HttpClientErrorException e) {
    		log.error("[checkNexusConnection] ## Message = " + e.getMessage(), e);
    		log.error("[checkNexusConnection] ## Response Code = " + e.getStatusCode().value());
    		log.error("[checkNexusConnection] ## Response Body = " + e.getResponseBodyAsString());
    		throw new NexusException(e.getStatusCode().value(), e.getMessage(), apiUri);
    	} catch ( RestClientException e ) {
    		log.error("요청하신 URL("+apiUri+")은 유효하지 않습니다.");
    		if ( e.getRootCause() instanceof SocketTimeoutException) {
    			log.error("SocketTimeoutException", e);
    			throw new NexusException(HttpStatus.REQUEST_TIMEOUT.value(), "The URL you requested is not valid.", apiUri);
    		} else if ( e.getRootCause() instanceof ConnectTimeoutException) {
    			log.error("ConnectTimeoutException", e);
    			throw new NexusException(HttpStatus.REQUEST_TIMEOUT.value(), "The URL you requested is not valid.", apiUri);
    		} else {
    			log.error(e.getMessage(), e);
    			throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), apiUri);
    		}
    	} catch (Exception ex) {
    		log.error("요청하신 URL("+apiUri+") 처리 중 에러가 발생했습니다.");
    		log.error(ex.getMessage(), ex);
    		throw new NexusException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An error occurred while processing the requested URL.", apiUri);
    	}
    }
}
