package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class IbmIngressAutomationPropertiesTest {
    @Test void shippedOperatorExampleBindsWithRealSpringPropertyRules() {
        var yaml=new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("doc/examples/ibm-ingress-automation/application-ibm-ingress.yaml"));
        Map<String,Object> values=new HashMap<>(); yaml.getObject().forEach((k,v) -> values.put(k.toString(),v));
        var config=new Binder(new MapConfigurationPropertySource(values)).bind("app.ibm-ingress.automation",Bindable.of(IbmIngressAutomationProperties.class)).get();
        var profile=config.profile("project-a","ibm-jp-osa");
        assertThat(profile).isNotNull(); assertThat(profile.isAllowSharedLbChanges()).isTrue();
        assertThat(profile.isInstallCertManager()).isTrue(); assertThat(profile.allowsHost("app.company.com")).isTrue();
        assertThat(profile.getApiKeyFile()).isEqualTo("/run/am-ingress/ibm-api-key");
        assertThat(config.getCertManagerVersion()).isEqualTo("v1.20.2");
        assertThat(config.profile("other-project","ibm-jp-osa")).isNull();
    }
    @Test void defaultIsOptInAndDuplicateTargetsFailClosed() {
        var config=new IbmIngressAutomationProperties(); assertThat(config.getProfiles()).isEmpty();
        var p=new IbmIngressAutomationProperties.Profile();
        assertThat(p.isAllowSharedLbChanges()).isFalse(); assertThat(p.isInstallCertManager()).isFalse();
        p.setNamespace("project-a"); p.setConnectionName("ibm-a"); config.setProfiles(List.of(p,p));
        assertThatThrownBy(() -> config.profile("project-a","ibm-a")).hasMessageContaining("Duplicate");
    }
}
