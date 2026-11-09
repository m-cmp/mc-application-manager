package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.repository.ApplicationStatusRepository;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.repository.OperationHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationHistoryService;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOperationService;
import kr.co.mcmp.softwarecatalog.application.service.DeploymentService;
import kr.co.mcmp.softwarecatalog.application.service.SpecValidationService;
import kr.co.mcmp.softwarecatalog.application.service.VmSecurityGroupExposureService;
import kr.co.mcmp.softwarecatalog.users.service.UserService;

@ExtendWith(MockitoExtension.class)
class ApplicationOrchestrationServiceProjectScopeTest {

    @Mock
    private ApplicationStatusRepository applicationStatusRepository;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationHistoryService applicationHistoryService;
    @Mock
    private SpecValidationService specValidationService;
    @Mock
    private DeploymentHistoryRepository deploymentHistoryRepository;
    @Mock
    private OperationHistoryRepository operationHistoryRepository;
    @Mock
    private VmSecurityGroupExposureService vmSecurityGroupExposureService;
    @Mock
    private VmNodeGroupTargetResolver vmNodeGroupTargetResolver;
    @Mock
    private VmInfraStateService vmInfraStateService;

    private ApplicationOrchestrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApplicationOrchestrationServiceImpl(
                applicationStatusRepository,
                userService,
                applicationHistoryService,
                specValidationService,
                List.<DeploymentService>of(),
                List.<ApplicationOperationService>of(),
                deploymentHistoryRepository,
                operationHistoryRepository,
                vmSecurityGroupExposureService,
                org.mockito.Mockito.mock(kr.co.mcmp.softwarecatalog.application.service.tunnel.ObjectStorageTunnelService.class),
                org.mockito.Mockito.mock(kr.co.mcmp.softwarecatalog.application.service.ObjectStorageAccessGrantService.class),
                org.mockito.Mockito.mock(kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService.class),
                vmNodeGroupTargetResolver, vmInfraStateService);
    }

    @Test
    void returnsOnlyStatusesLoadedForRequestedNamespaceAndKeepsStableNewestFirstOrder() {
        ApplicationStatus older = ApplicationStatus.builder()
                .id(1L)
                .namespace("project-a")
                .checkedAt(LocalDateTime.of(2026, 8, 30, 10, 0))
                .build();
        ApplicationStatus newer = ApplicationStatus.builder()
                .id(2L)
                .namespace("project-a")
                .checkedAt(LocalDateTime.of(2026, 8, 31, 10, 0))
                .build();
        when(applicationStatusRepository.findByNamespace("project-a"))
                .thenReturn(List.of(older, newer));

        List<ApplicationStatusDto> result = service.getApplicationGroups("project-a");

        assertThat(result).extracting(ApplicationStatusDto::getId).containsExactly(2L, 1L);
        assertThat(result).allMatch(status -> "project-a".equals(status.getNamespace()));
        verify(applicationStatusRepository).findByNamespace("project-a");
    }

    @Test
    void rejectsBlankNamespaceInsteadOfFallingBackToAllProjects() {
        assertThatThrownBy(() -> service.getApplicationGroups(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Namespace");
    }

    @Test
    void hidesOnlyConfirmedDeletedInfraAndKeepsUninstalledAndK8sRecordsWithoutDeletingData() {
        var removed = ApplicationStatus.builder().id(1L).namespace("project-a").mciId("removed")
                .deploymentType(kr.co.mcmp.softwarecatalog.application.constants.DeploymentType.VM).status("UNINSTALLED").build();
        var existing = ApplicationStatus.builder().id(2L).namespace("project-a").mciId("existing")
                .deploymentType(kr.co.mcmp.softwarecatalog.application.constants.DeploymentType.VM).status("UNINSTALLED").build();
        var k8s = ApplicationStatus.builder().id(3L).namespace("project-a").clusterName("k1")
                .deploymentType(kr.co.mcmp.softwarecatalog.application.constants.DeploymentType.K8S).status("UNINSTALLED").build();
        when(applicationStatusRepository.findByNamespace("project-a")).thenReturn(List.of(removed, existing, k8s));
        when(vmInfraStateService.isDeleted("project-a", "removed")).thenReturn(true);
        assertThat(service.getApplicationGroups("project-a")).extracting(ApplicationStatusDto::getId).containsExactly(2L, 3L);
        org.mockito.Mockito.verify(applicationStatusRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(applicationStatusRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }
    @Test
    void legacyK8sFailureResolvesItsHistoryOnlyWithinExactTargetWithoutChangingEntity() {
        var catalog = new kr.co.mcmp.softwarecatalog.SoftwareCatalog(); catalog.setId(12L);
        var failed = ApplicationStatus.builder().namespace("project-a").clusterName("cluster-a").catalog(catalog)
                .deploymentType(kr.co.mcmp.softwarecatalog.application.constants.DeploymentType.K8S)
                .status("FAILED").deploymentHistoryId(3L).build();
        when(applicationStatusRepository.findByNamespace("project-a")).thenReturn(List.of(failed));
        when(deploymentHistoryRepository.findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeInAndStatusInOrderByExecutedAtDesc(
                12L,"cluster-a","project-a",List.of(kr.co.mcmp.softwarecatalog.application.constants.ActionType.INSTALL),
                List.of("FAILED","DELETE_PENDING"))).thenReturn(java.util.Optional.of(
                        kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory.builder().id(42L).build()));
        assertThat(service.getApplicationGroups("project-a").get(0).getDeploymentHistoryId()).isEqualTo(42L);
        assertThat(failed.getDeploymentHistoryId()).isEqualTo(3L);
        org.mockito.Mockito.verify(applicationStatusRepository,org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
