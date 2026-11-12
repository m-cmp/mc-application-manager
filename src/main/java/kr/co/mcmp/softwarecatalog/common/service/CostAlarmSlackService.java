package kr.co.mcmp.softwarecatalog.common.service;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CostAlarmSlackService {

    private final RestTemplate restTemplate;
    private final String url;
    private final String userId;
    private final String clusterName;

    public CostAlarmSlackService(
            @Value("${cost-alarm.slack.url:}") String url,
            @Value("${cost-alarm.slack.user-id:}") String userId,
            @Value("${cost-alarm.slack.cluster-name:}") String clusterName) {
        this.url = url;
        this.userId = userId;
        this.clusterName = clusterName;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public boolean isConfiguredFor(String cluster) {
        return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(userId)
                && (StringUtils.isBlank(clusterName) || clusterName.equals(cluster));
    }

    public boolean send(String title, String message) {
        try {
            URI endpoint = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("userId", "{userId}")
                    .queryParam("message", "{message}")
                    .encode()
                    .buildAndExpand(userId, title + "\n" + message)
                    .toUri();

            // Cost Alarm returns HTTP 200 only after Slack accepts chat.postMessage.
            return restTemplate.postForEntity(endpoint, null, String.class).getStatusCode() == HttpStatus.OK;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Cost Alarm Slack post failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
