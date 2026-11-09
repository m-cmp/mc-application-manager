package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.*;
import kr.co.mcmp.softwarecatalog.application.repository.*;
import kr.co.mcmp.softwarecatalog.application.service.impl.VmInfraStateService;
import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;

@ExtendWith(MockitoExtension.class)
class DockerMonitoringInfraStateTest {
    @Mock ApplicationStatusRepository statuses;
    @Mock DeploymentHistoryRepository deployments;
    @Mock ResourceMetricsHistoryRepository metrics;
    @Mock AbnormalEventRepository events;
    @Mock OperationHistoryRepository operations;
    @Mock DockerOperationService docker;
    @Mock ContainerStatsCollector stats;
    @Mock DockerLogCollector logs;
    @Mock VmInfraStateService infra;
    @InjectMocks DockerMonitoringService service;
    ApplicationStatus status;
    DeploymentHistory deployment;

    @BeforeEach void setup() {
        var catalog = new SoftwareCatalog(); catalog.setId(1L); catalog.setName("Nginx");
        deployment = DeploymentHistory.builder().id(5L).catalog(catalog).namespace("p1").mciId("m1").vmId("v1")
                .containerId("a".repeat(64)).deploymentType(DeploymentType.VM).actionType(ActionType.INSTALL)
                .status("SUCCESS").executedAt(LocalDateTime.now()).build();
        status = ApplicationStatus.builder().catalog(catalog).namespace("p1").mciId("m1").vmId("v1")
                .deploymentType(DeploymentType.VM).status("ERROR").cpuUsage(50.0).build();
        when(statuses.findAll()).thenReturn(List.of(status));
        when(deployments.findAll()).thenReturn(List.of(deployment));
        when(statuses.findByCatalogIdAndNamespaceAndMciIdAndVmId(1L,"p1","m1","v1")).thenReturn(Optional.of(status));
    }

    @Test void suspendedSkipsSshAndDoesNotInventMetricsOrAbnormalEvents() {
        var vm = new VmAccessInfo(); vm.setStatus("Suspended");
        when(infra.findVm("p1","m1","v1")).thenReturn(vm);
        service.monitorContainerHealth();
        assertThat(status.getStatus()).isEqualTo("SUSPENDED");
        assertThat(status.getCpuUsage()).isNull();
        assertThat(status.getIsHealthCheck()).isNull();
        verifyNoInteractions(docker, stats, metrics, events, logs);
        verify(statuses).save(status);
    }

    @Test void resumingRestoresNormalMonitoringAndSnapshotCollection() {
        status.setStatus("SUSPENDED");
        var vm = new VmAccessInfo(); vm.setStatus("Running");
        when(infra.findVm("p1","m1","v1")).thenReturn(vm);
        when(stats.collectContainerStats(any(),anyString())).thenReturn(
                ContainerHealthInfo.builder().status("RUNNING").cpuUsage(12.0).memoryUsage(10.0).build());
        service.monitorContainerHealth();
        assertThat(status.getStatus()).isEqualTo("RUNNING");
        assertThat(status.getCpuUsage()).isEqualTo(12.0);
        verify(metrics).save(any(ResourceMetricsHistory.class));
    }

    @Test void uninstalledRecordIsNotReclassifiedWhenVmSuspends() {
        status.setStatus("UNINSTALLED");
        service.monitorContainerHealth();
        assertThat(status.getStatus()).isEqualTo("UNINSTALLED");
        verifyNoInteractions(docker, stats, metrics, events);
    }

    @Test void missingInfraSkipsSshWithoutDeletingHistoryOrArchives() {
        when(infra.isDeleted("p1","m1")).thenReturn(true);
        service.monitorContainerHealth();
        verifyNoInteractions(docker, stats, metrics, events);
        verify(statuses,never()).delete(any());
        verify(deployments,never()).delete(any());
    }
}
