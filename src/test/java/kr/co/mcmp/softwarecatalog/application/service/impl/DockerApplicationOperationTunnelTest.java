package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.*;
import kr.co.mcmp.softwarecatalog.application.service.*;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.ObjectStorageTunnelService;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;
import kr.co.mcmp.softwarecatalog.docker.service.DockerOperationService;

@ExtendWith(MockitoExtension.class)
class DockerApplicationOperationTunnelTest {
    @Mock ApplicationStatusRepository statuses;
    @Mock DeploymentHistoryRepository histories;
    @Mock DockerOperationService docker;
    @Mock ApplicationHistoryService historyService;
    @Mock ObjectStorageAccessGrantService grants;
    @Mock ObjectStorageTunnelService tunnels;
    @Mock VmSecurityGroupExposureService exposure;
    @Mock VmInfraStateService infraStates;
    @InjectMocks DockerApplicationOperationService service;
    ApplicationStatus status;
    DockerTarget target=new DockerTarget("default","infra","vm");
    String cid="a".repeat(64);
    @BeforeEach void setup(){
        SoftwareCatalog catalog=new SoftwareCatalog();
        catalog.setName("Jupyter");
        status=ApplicationStatus.builder().id(2L).namespace("default").mciId("infra").vmId("vm")
                .catalog(catalog).containerId(cid).deploymentHistoryId(1L).build();
        when(statuses.findById(2L)).thenReturn(Optional.of(status));
    }
    @Test void restartSuspendsTunnelBeforeRestartAndResumesAfter(){
        assertThat(service.performOperation(ActionType.RESTART,2L,"test","user")).containsEntry("success",true);
        var order=inOrder(tunnels,docker);
        order.verify(tunnels).suspend(target,cid);
        order.verify(docker).restartDockerContainer(target,cid);
        order.verify(tunnels).resume(target,cid);
    }
    @Test void stopSuspendsBeforeStoppingParent(){
        service.performOperation(ActionType.STOP,2L,"test","user");
        var order=inOrder(tunnels,docker);
        order.verify(tunnels).suspend(target,cid);
        order.verify(docker).stopDockerContainer(target,cid);
    }
    @Test void startResumesAfterParentStarts(){
        service.performOperation(ActionType.START,2L,"test","user");
        var order=inOrder(tunnels,docker);
        order.verify(docker).startDockerContainer(target,cid);
        order.verify(tunnels).resume(target,cid);
    }
    @Test void uninstallRemovesTunnelThenParentThenGrantsAndInboundRule(){
        DeploymentHistory history=new DeploymentHistory();
        history.setId(1L);
        when(histories.findById(1L)).thenReturn(Optional.of(history));
        assertThat(service.performOperation(ActionType.UNINSTALL,2L,"test","user")).containsEntry("success",true);
        var order=inOrder(tunnels,docker,grants,exposure);
        order.verify(tunnels).remove(target,cid);
        order.verify(docker).removeDockerContainer(target,cid);
        order.verify(tunnels).remove(1L);
        order.verify(grants).revoke(1L,"vm");
        order.verify(exposure).releaseRestrictedInboundRule(1L);
    }
    @Test void cleanupFailureDoesNotReportSuccessfulUninstall(){
        doThrow(new IllegalStateException("cleanup pending")).when(tunnels).remove(target,cid);
        assertThat(service.performOperation(ActionType.UNINSTALL,2L,"test","user")).containsEntry("success",false);
        verify(docker,never()).removeDockerContainer(target,cid);
    }
    @Test void suspendedVmRejectsOperationsBeforeTunnelDockerOrHistoryChanges(){
        for (ActionType action : java.util.List.of(ActionType.RESTART,ActionType.STOP,ActionType.UNINSTALL)) {
            doThrow(new IllegalStateException("VM is Suspended")).when(infraStates).assertActionAllowed(status,action);
            assertThat(service.performOperation(action,2L,"test","user"))
                .containsEntry("success",false).containsEntry("error","VM is Suspended");
        }
        verifyNoInteractions(docker,tunnels,grants,exposure,historyService);
    }
}
