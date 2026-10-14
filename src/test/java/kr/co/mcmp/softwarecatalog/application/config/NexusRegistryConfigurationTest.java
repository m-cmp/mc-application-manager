package kr.co.mcmp.softwarecatalog.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

class NexusRegistryConfigurationTest {
    private ApplicationContextRunner runner() {
        var yaml = new YamlPropertiesFactoryBean(); yaml.setResources(new ClassPathResource("application.yaml"));
        var properties = yaml.getObject();
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(NexusConfig.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(new PropertiesPropertySource("actual-application-yaml", properties)));
    }
    @Test void actualYamlWithUnsetOptionalEndpointStartsWithoutReadOnlyPropertyConflict() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(NexusConfig.class).getRegistryEndpoint()).isEmpty();
            assertThat(context.getBean(NexusConfig.class).getDockerPort()).isEqualTo(5000);
        });
    }
    @Test void configuredEndpointDoesNotChangeLegacyDockerUrlGetter() {
        runner().withPropertyValues("nexus.registry-endpoint=https://registry.example:5443").run(context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(NexusConfig.class);
            assertThat(config.getRegistryEndpoint()).isEqualTo("https://registry.example:5443");
            assertThat(config.getDockerRegistryUrl()).isEqualTo("localhost:5000");
        });
    }
}
