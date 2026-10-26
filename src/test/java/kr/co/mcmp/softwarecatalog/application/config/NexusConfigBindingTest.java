package kr.co.mcmp.softwarecatalog.application.config;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class NexusConfigBindingTest {
    @Test
    void registryEnvironmentVariablesBindAlongsideLegacyComputedProperties() throws IOException {
        var properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(NexusConfig.class)
                .withInitializer(context -> {
                    properties.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    context.getEnvironment().getPropertySources().addFirst(
                            new SystemEnvironmentPropertySource("registry-test-env", Map.of(
                                    "NEXUS_REGISTRY_ENDPOINT", "http://nexus-internal:5000",
                                    "NEXUS_DEPLOYMENT_REGISTRY_ENDPOINT", "http://52.24.20.63:5500")));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var config = context.getBean(NexusConfig.class);
                    assertThat(config.getRegistryEndpoint()).isEqualTo("http://nexus-internal:5000");
                    assertThat(config.getDeploymentRegistryEndpoint()).isEqualTo("http://52.24.20.63:5500");
                });
    }
}
