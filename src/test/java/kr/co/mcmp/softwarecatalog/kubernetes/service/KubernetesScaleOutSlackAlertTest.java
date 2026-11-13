package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.common.service.RabbitMqAlertService;

class KubernetesScaleOutSlackAlertTest {
    private static final String CHANNEL_ID = "C0123456789";
    private static final String PUBLISH_URL =
            "http://mc-observability-rabbitmq:15672/api/exchanges/%2ftest/alert-manual.exchange/publish";

    private static final String POLICY_URL = "http://mc-observability-manager:18080/api/o11y/trigger/policy";

    @Test
    void publishesDistinctActiveSlackRecipientsFromTheNamedMonitoringPolicy() throws IOException {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var monitoring = mockHttp(context.getBean(ObservabilitySlackRecipients.class));
            monitoring.expect(requestTo(POLICY_URL + "?page=1&size=50")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {"data":{"totalPages":2,"content":[{"title":"Other policy","notiChannels":[
                                {"type":"slack","isActive":true,"recipients":["C9999999999"]}
                            ]}]}}
                            """, MediaType.APPLICATION_JSON));
            monitoring.expect(requestTo(POLICY_URL + "?page=2&size=50")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {"data":{"totalPages":2,"content":[{"title":"AM K8s 증설 알림","notiChannels":[
                                {"type":"slack","isActive":true,"recipients":["C0123456789"," C0123456789 ","G0123456789",""]},
                                {"type":"slack","isActive":false,"recipients":["C2222222222"]},
                                {"type":"email","isActive":true,"recipients":["ops@example.com"]}
                            ]}]}}
                            """, MediaType.APPLICATION_JSON));

            var rabbitMq = mockHttp(context.getBean(RabbitMqAlertService.class));
            expectSlackPublish(rabbitMq, CHANNEL_ID, "G0123456789");
            sendScaleOutCompletedAlert(context.getBean(KubernetesMonitoringService.class));
            monitoring.verify();
            rabbitMq.verify();
        });
    }

    @Test
    void keepsAnExplicitChannelOverrideWithoutQueryingMonitoring() throws IOException {
        contextRunner().withPropertyValues("ALERT_SLACK_CHANNEL_ID=" + CHANNEL_ID).run(context -> {
            assertThat(context).hasNotFailed();
            var monitoring = mockHttp(context.getBean(ObservabilitySlackRecipients.class));
            var rabbitMq = mockHttp(context.getBean(RabbitMqAlertService.class));

            expectSlackPublish(rabbitMq, CHANNEL_ID);
            sendScaleOutCompletedAlert(context.getBean(KubernetesMonitoringService.class));
            monitoring.verify();
            rabbitMq.verify();
        });
    }

    @Test
    void publishesNoSlackAlertWhenMonitoringIsUnavailable() throws IOException {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var monitoring = mockHttp(context.getBean(ObservabilitySlackRecipients.class));
            monitoring.expect(requestTo(POLICY_URL + "?page=1&size=50")).andRespond(withServerError());
            var rabbitMq = mockHttp(context.getBean(RabbitMqAlertService.class));

            sendScaleOutCompletedAlert(context.getBean(KubernetesMonitoringService.class));
            monitoring.verify();
            rabbitMq.verify();
        });
    }

    @Test
    void doesNotReportUnroutedMessagesAsSent() {
        var rabbitMq = new RabbitMqAlertService();
        ReflectionTestUtils.setField(rabbitMq, "rabbitmqUrl", "mc-observability-rabbitmq");
        ReflectionTestUtils.setField(rabbitMq, "rabbitmqPort", "15672");
        ReflectionTestUtils.setField(rabbitMq, "vhost", "test");
        ReflectionTestUtils.setField(rabbitMq, "username", "test");
        ReflectionTestUtils.setField(rabbitMq, "password", "test");
        var server = mockHttp(rabbitMq);
        server.expect(requestTo(PUBLISH_URL))
                .andRespond(withSuccess("{\"routed\":false}", MediaType.APPLICATION_JSON));
        assertThat(rabbitMq.sendScaleOutAlert("Test", "Test", "slack", CHANNEL_ID)).isFalse();
        server.verify();
    }

    @Configuration(proxyBeanMethods = false)
    static class MonitoringConfig {
        @Bean
        KubernetesMonitoringService monitoringService(
                RabbitMqAlertService rabbitMq, ObservabilitySlackRecipients observabilitySlackRecipients) {
            return new KubernetesMonitoringService(
                    null, null, null, null, null, null, null, null, null, null, rabbitMq, null,
                    observabilitySlackRecipients);
        }
    }

    private static ApplicationContextRunner contextRunner() throws IOException {
        var sources = new YamlPropertySourceLoader().load("am", new ClassPathResource("application.yaml"));
        return new ApplicationContextRunner()
                .withInitializer(context -> sources.forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withPropertyValues("RABBITMQ_URL=mc-observability-rabbitmq")
                .withBean(RabbitMqAlertService.class)
                .withBean(ObservabilitySlackRecipients.class)
                .withUserConfiguration(MonitoringConfig.class);
    }

    private static void expectSlackPublish(MockRestServiceServer server, String... expectedRecipients) {
        var json = new ObjectMapper();
        server.expect(requestTo(PUBLISH_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("routing_key").asText()).isEqualTo("alert-manual.queue");
                    var alert = json.readTree(body.path("payload").asText());
                    assertThat(alert.path("channelName").asText()).isEqualTo("slack");
                    assertThat(json.convertValue(alert.path("recipients"), String[].class))
                            .containsExactlyElementsOf(List.of(expectedRecipients));
                    assertThat(alert.path("message").asText()).contains("2 nodes", "tc2-test");
                })
                .andRespond(withSuccess("{\"routed\":true}", MediaType.APPLICATION_JSON));
    }

    private static void sendScaleOutCompletedAlert(KubernetesMonitoringService monitoring) {
        var catalog = new SoftwareCatalog();
        catalog.setName("Nginx");
        var deployment = new DeploymentHistory();
        deployment.setCatalog(catalog);
        deployment.setClusterName("tc2-test");
        deployment.setNamespace("default");
        ReflectionTestUtils.invokeMethod(monitoring, "sendScaleOutCompletedAlert", deployment, 2);
    }

    private static MockRestServiceServer mockHttp(Object service) {
        var template = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        return MockRestServiceServer.bindTo(template).build();
    }
}
