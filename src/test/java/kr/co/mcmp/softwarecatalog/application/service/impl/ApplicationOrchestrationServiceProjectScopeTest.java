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
                vmNodeGroupTargetResolver);
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
}
