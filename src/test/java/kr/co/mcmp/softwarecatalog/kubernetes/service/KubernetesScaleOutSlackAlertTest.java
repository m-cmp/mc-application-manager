package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
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

    @Test
    void sendsTheYamlChannelToObservabilityRabbitMq() throws IOException {
        String yaml = new ClassPathResource("application.yaml").getContentAsString(StandardCharsets.UTF_8);
        String configuredYaml = yaml.replace("${ALERT_SLACK_CHANNEL_ID:}", CHANNEL_ID);
        assertThat(configuredYaml).isNotEqualTo(yaml);
        var sources = new YamlPropertySourceLoader()
                .load("configured-am", new ByteArrayResource(configuredYaml.getBytes(StandardCharsets.UTF_8)));

        new ApplicationContextRunner()
                .withInitializer(context -> sources.forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withPropertyValues("RABBITMQ_URL=mc-observability-rabbitmq")
                .withBean(RabbitMqAlertService.class)
                .withUserConfiguration(MonitoringConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var rabbitMq = context.getBean(RabbitMqAlertService.class);
                    var server = mockBroker(rabbitMq);
                    var json = new ObjectMapper();
                    server.expect(requestTo(PUBLISH_URL)).andExpect(method(HttpMethod.POST))
                            .andExpect(request -> {
                                var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                                assertThat(body.path("routing_key").asText()).isEqualTo("alert-manual.queue");
                                var alert = json.readTree(body.path("payload").asText());
                                assertThat(alert.path("channelName").asText()).isEqualTo("slack");
                                assertThat(alert.path("recipients").get(0).asText()).isEqualTo(CHANNEL_ID);
                                assertThat(alert.path("message").asText()).contains("2 nodes", "tc2-test");
                            })
                            .andRespond(withSuccess("{\"routed\":true}", MediaType.APPLICATION_JSON));
                    var catalog = new SoftwareCatalog();
                    catalog.setName("Nginx");
                    var deployment = new DeploymentHistory();
                    deployment.setCatalog(catalog);
                    deployment.setClusterName("tc2-test");
                    deployment.setNamespace("default");
                    ReflectionTestUtils.invokeMethod(context.getBean(KubernetesMonitoringService.class),
                            "sendScaleOutCompletedAlert", deployment, 2);
                    server.verify();
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
        var server = mockBroker(rabbitMq);
        server.expect(requestTo(PUBLISH_URL))
                .andRespond(withSuccess("{\"routed\":false}", MediaType.APPLICATION_JSON));
        assertThat(rabbitMq.sendScaleOutAlert("Test", "Test", "slack", CHANNEL_ID)).isFalse();
        server.verify();
    }

    @Configuration(proxyBeanMethods = false)
    static class MonitoringConfig {
        @Bean
        KubernetesMonitoringService monitoringService(RabbitMqAlertService rabbitMq) {
            return new KubernetesMonitoringService(
                    null, null, null, null, null, null, null, null, null, null, rabbitMq, null);
        }
    }

    private static MockRestServiceServer mockBroker(RabbitMqAlertService rabbitMq) {
        var template = (RestTemplate) ReflectionTestUtils.getField(rabbitMq, "restTemplate");
        return MockRestServiceServer.bindTo(template).build();
    }
}
