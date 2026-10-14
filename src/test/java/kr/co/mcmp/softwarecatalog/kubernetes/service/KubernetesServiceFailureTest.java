package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.*;
import kr.co.mcmp.softwarecatalog.application.repository.*;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentFailureException;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KubernetesServiceFailureTest {
    @Mock KubernetesDeployService deploy;
    @Mock K8sJupyterService jupyter;
    @Mock KubernetesOperationService operations;
    @Mock DeploymentHistoryRepository histories;
    @Mock ApplicationStatusRepository statuses;
    @Mock CatalogRepository catalogs;
    @Mock DeploymentLogRepository logs;
    @Mock InfraSpecSnapshotRepository snapshots;
    @Mock UserRepository users;
    @InjectMocks KubernetesService service;
    SoftwareCatalog catalog;
    DeploymentRequest request;
    @BeforeEach void setup() {
        catalog = new SoftwareCatalog(); catalog.setId(12L);
        request = DeploymentRequest.builder().catalogId(12L).namespace("p5").clusterName("k5").username("user").build();
        when(catalogs.findById(12L)).thenReturn(Optional.of(catalog));
        when(deploy.deployApplication(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Enable Ingress to configure external CIDR access."));
    }
    @Test void firstFailureLinksSavedHistoryAndRecordsReason() {
        when(histories.save(any())).thenAnswer(i -> { DeploymentHistory h=i.getArgument(0); h.setId(42L); return h; });
        assertThatThrownBy(() -> service.deployApplication(request)).isInstanceOfSatisfying(DeploymentFailureException.class,
                e -> assertThat(e.getDeploymentId()).isEqualTo(42L));
        verify(statuses).save(argThat(s -> "FAILED".equals(s.getStatus()) && Long.valueOf(42).equals(s.getDeploymentHistoryId())));
        verify(logs).save(argThat(l -> l.getLogMessage().contains("Enable Ingress")));
    }
    @Test void repeatedFailureReplacesOldHistoryAndDoesNotOpenPreviousSuccess() {
        ApplicationStatus existing=ApplicationStatus.builder().deploymentHistoryId(10L).build();
        when(statuses.findLatestByNamespaceAndClusterNameAndCatalogId("p5","k5",12L)).thenReturn(Optional.of(existing));
        when(histories.save(any())).thenAnswer(i -> { DeploymentHistory h=i.getArgument(0); h.setId(43L); return h; });
        assertThatThrownBy(() -> service.deployApplication(request)).isInstanceOf(DeploymentFailureException.class);
        assertThat(existing.getDeploymentHistoryId()).isEqualTo(43L);
    }
    @Test void persistenceFailureDoesNotPretendOldHistoryIsTheNewFailure() {
        ApplicationStatus existing=ApplicationStatus.builder().deploymentHistoryId(10L).build();
        when(statuses.findLatestByNamespaceAndClusterNameAndCatalogId("p5","k5",12L)).thenReturn(Optional.of(existing));
        when(histories.save(any())).thenThrow(new IllegalStateException("DB unavailable"));
        assertThatThrownBy(() -> service.deployApplication(request)).isInstanceOfSatisfying(DeploymentFailureException.class,
                e -> assertThat(e.getDeploymentId()).isNull());
        assertThat(existing.getDeploymentHistoryId()).isNull();
    }
}
