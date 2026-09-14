package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.mockito.Mockito.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;

/** Preserve legacy ready/not-ready assertions; automatic provisioning has separate integration tests. */
final class IbmIngressAutomationTestSupport {
    static IbmIngressAutomationService legacy() {
        var automation = mock(IbmIngressAutomationService.class);
        when(automation.check(any(), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            KubernetesClient client = call.getArgument(0);
            DeploymentConfigDTO config = IbmIngressTlsResolver.resolve(client, call.getArgument(3), call.getArgument(4));
            return IbmIngressSupport.verify(client, config);
        });
        when(automation.prepare(any(), anyString(), anyString(), anyString(), any(), any())).thenAnswer(call -> {
            KubernetesClient client = call.getArgument(0);
            DeploymentConfigDTO config = IbmIngressTlsResolver.resolve(client, call.getArgument(3), call.getArgument(4));
            IbmIngressSupport.verify(client,config);
            return config;
        });
        return automation;
    }
}
