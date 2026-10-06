package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NhnCinderAddonConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(NhnCinderAddonProperties.class, NhnOpenBaoCredentials.class, NhnNksApi.class,
                    NhnCinderAddonService.class, NhnCinderAddonJobs.class)
            .withBean(CbtumblebugRestApi.class, () -> mock(CbtumblebugRestApi.class))
            .withBean(KubernetesClientFactory.class, () -> mock(KubernetesClientFactory.class));

    @Test void springCreatesAllComponentsWithAutomationDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NhnCinderAddonService.class).hasSingleBean(NhnCinderAddonJobs.class);
            assertThat(context.getBean(NhnCinderAddonProperties.class).isEnabled()).isFalse();
        });
    }
    @Test void documentedYamlPropertyNamesBindToTheProjectCredentialConnection() {
        runner.withPropertyValues("app.nhn-cinder-addon.enabled=true", "app.nhn-cinder-addon.open-bao-url=http://openbao:8200",
                "app.nhn-cinder-addon.open-bao-token-file=/run/secrets/am-nhn-openbao-token",
                "app.nhn-cinder-addon.bindings[0].namespace=project-a", "app.nhn-cinder-addon.bindings[0].connection-name=nhn-kr1",
                "app.nhn-cinder-addon.bindings[0].region=kr1", "app.nhn-cinder-addon.bindings[0].secret-path=secret/data/csp/nhn")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(NhnCinderAddonProperties.class);
                    assertThat(properties.getOpenBaoUrl()).isEqualTo("http://openbao:8200");
                    assertThat(properties.getOpenBaoTokenFile()).isEqualTo("/run/secrets/am-nhn-openbao-token");
                    assertThat(properties.binding("project-a", "nhn-kr1").getSecretPath()).isEqualTo("secret/data/csp/nhn");
                });
    }
}
