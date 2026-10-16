package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class IbmClusterIdentityStartupTest {
    @Test void clusterAuthenticationStartsWithoutLegacyDatabaseOrAValidEncryptionPath() {
        // No JdbcTemplate bean and an unusable legacy key path: cluster mode must still start.
        try(var context=new AnnotationConfigApplicationContext()) {
            var properties=new IbmIngressAutomationProperties();properties.setCredentialKeyFile("unavailable-relative-key");
            context.registerBean(IbmIngressAutomationProperties.class,()->properties);
            context.registerBean(IbmClusterIdentityTokens.class,()->mock(IbmClusterIdentityTokens.class));
            context.register(IbmCloudIngressApi.class,IbmOpenBaoCredentials.class,IbmStoredOpenBaoConfiguration.class);
            context.refresh();
            assertThat(context.getBean(IbmCloudIngressApi.class)).isNotNull();
            assertThat(context.getBeanFactory().containsSingleton("ibmOpenBaoCredentials")).isFalse();
            assertThat(context.getBeanFactory().containsSingleton("ibmStoredOpenBaoConfiguration")).isFalse();
        }
    }
}
