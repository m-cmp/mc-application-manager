package kr.co.mcmp.softwarecatalog.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ를 통한 알람 전송 서비스
 */
@Slf4j
@Service
public class RabbitMqAlertService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${rabbitmq.alert.url:localhost}")
    private String rabbitmqUrl;
    
    @Value("${rabbitmq.alert.port:15672}")
    private String rabbitmqPort;
    
    @Value("${rabbitmq.alert.vhost:test}")
    private String vhost;
    
    @Value("${rabbitmq.alert.username:mc-agent}")
    private String username;
    
    @Value("${rabbitmq.alert.password:mc-agent}")
    private String password;
    
    @Value("${rabbitmq.alert.slack-channel-id:#kubernetes-alerts}")
    private String defaultSlackChannelId;
    
    public RabbitMqAlertService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 스케일 아웃 완료 알람 전송
     * 
     * @param title 알람 제목
     * @param message 알람 메시지
     * @param channelName 발송 채널 (slack|email|sms|kakao)
     * @param recipients 수신자 정보 (채널 ID, 이메일, 전화번호 등)
     * @return 전송 성공 여부
     */
    public boolean sendScaleOutAlert(String title, String message, String channelName, String recipients) {
        try {
            // RabbitMQ HTTP API URL 생성 (담당자 예시에 맞춤)
            String url = String.format("http://%s:%s/api/exchanges/%%2F%s/amq.default/publish", 
                    rabbitmqUrl, rabbitmqPort, vhost);
            
            log.info("Sending alert to RabbitMQ: {}", url);
            
            // 알람 메시지 생성
            AlertMessage alertMessage = new AlertMessage();
            alertMessage.setTitle(title);
            alertMessage.setMessage(message);
            alertMessage.setChannelName(channelName);
            alertMessage.setRecipients(recipients);
            
            // JSON 문자열로 변환
            String payload = objectMapper.writeValueAsString(alertMessage);
            
            // RabbitMQ 요청 본문 생성
            RabbitMqRequest request = new RabbitMqRequest();
            request.setRoutingKey("alert.queue");  // 담당자 예시에 맞춤
            request.setPayload(payload);
            request.setPayloadEncoding("string");
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            log.info("Alert payload: {}", payload);
            log.info("RabbitMQ request body: {}", requestBody);
            
            // HTTP 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(username, password);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            // RabbitMQ에 POST 요청
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    entity, 
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully sent alert to RabbitMQ: {}", response.getBody());
                return true;
            } else {
                log.error("Failed to send alert to RabbitMQ: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error sending alert to RabbitMQ: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 알람 메시지 DTO
     */
    public static class AlertMessage {
        private String title;
        private String message;
        private String channelName;
        private String recipients;
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }
        
        public String getRecipients() { return recipients; }
        public void setRecipients(String recipients) { this.recipients = recipients; }
    }
    
    /**
     * RabbitMQ HTTP API 요청 DTO
     */
    public static class RabbitMqRequest {
        private Object properties = new Object();
        private String routingKey;
        private String payload;
        private String payloadEncoding;
        
        // Getters and Setters
        public Object getProperties() { return properties; }
        public void setProperties(Object properties) { this.properties = properties; }
        
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        
        public String getPayloadEncoding() { return payloadEncoding; }
        public void setPayloadEncoding(String payloadEncoding) { this.payloadEncoding = payloadEncoding; }
    }
}
