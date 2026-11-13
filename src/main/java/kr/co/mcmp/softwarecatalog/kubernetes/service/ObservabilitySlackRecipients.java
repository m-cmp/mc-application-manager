package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
class ObservabilitySlackRecipients {

    private static final String POLICY_TITLE = "AM K8s 증설 알림";

    private final RestTemplate restTemplate;
    private final String policyListUrl;

    ObservabilitySlackRecipients(@Value("${observability.alert.base-url}") String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.policyListUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("api", "o11y", "trigger", "policy")
                .queryParam("page", "{page}")
                .queryParam("size", 50)
                .build()
                .toUriString();
    }

    List<String> findScaleOutRecipients() {
        try {
            int page = 1;
            int totalPages;
            do {
                JsonNode response = restTemplate.getForObject(policyListUrl, JsonNode.class, page);
                JsonNode data = response == null ? null : response.path("data");
                if (data == null || !data.path("content").isArray() || !data.path("totalPages").isIntegralNumber()) {
                    log.warn("Unexpected response while reading Monitoring policy '{}'", POLICY_TITLE);
                    return List.of();
                }

                for (JsonNode policy : data.path("content")) {
                    if (POLICY_TITLE.equals(policy.path("title").asText())) {
                        List<String> recipients = getActiveSlackRecipients(policy.path("notiChannels"));
                        if (recipients.isEmpty()) {
                            log.info("Monitoring policy '{}' has no active Slack recipients", POLICY_TITLE);
                        }
                        return recipients;
                    }
                }
                totalPages = data.path("totalPages").asInt();
            } while (++page <= totalPages);
        } catch (RestClientException e) {
            log.warn("Could not read Monitoring policy '{}': {}", POLICY_TITLE, e.getClass().getSimpleName());
            return List.of();
        }

        log.info("Monitoring policy '{}' was not found", POLICY_TITLE);
        return List.of();
    }

    private List<String> getActiveSlackRecipients(JsonNode channels) {
        Set<String> recipients = new LinkedHashSet<>();
        for (JsonNode channel : channels) {
            if ("slack".equalsIgnoreCase(channel.path("type").asText()) && channel.path("isActive").asBoolean()) {
                for (JsonNode recipient : channel.path("recipients")) {
                    if (recipient.isTextual() && !recipient.textValue().isBlank()) {
                        recipients.add(recipient.textValue().strip());
                    }
                }
            }
        }
        return List.copyOf(recipients);
    }
}
