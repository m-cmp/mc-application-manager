package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.dto.*;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;
import kr.co.mcmp.softwarecatalog.application.service.*;
import kr.co.mcmp.softwarecatalog.application.service.tunnel.ObjectStorageTunnelService;
import kr.co.mcmp.softwarecatalog.docker.model.*;
import kr.co.mcmp.softwarecatalog.docker.service.*;

@ExtendWith(MockitoExtension.class)
class DockerDeploymentTunnelTest {
    @Mock DockerSetupService setup;
    @Mock DockerOperationService docker;
    @Mock CbtumblebugRestApi tumblebug;
    @Mock VmSecurityGroupExposureService exposure;
    @Mock ApplicationHistoryService historyService;
    @Mock DeploymentHistoryRepository histories;
    @Mock ObjectStorageAccessGrantService grants;
    @Mock ObjectStorageTunnelService tunnels;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @InjectMocks DockerDeploymentService service;
    DeploymentRequest request;
    DeploymentHistory history;
    SoftwareCatalogDTO catalog;
    String cid="a".repeat(64);
    DockerTarget target=new DockerTarget("default","infra","vm");

    @BeforeEach void setup(){
        request=new DeploymentRequest();
        request.setNamespace("default");request.setMciId("infra");request.setServicePort(8888);
        request.setVmIds(List.of("vm"));
        request.setAdditionalConfig(Map.of("objectStorage",Map.of("enabled",true,"jupyterToken","test-only-token-12345")));
        history=new DeploymentHistory();history.setId(1L);
        catalog=SoftwareCatalogDTO.builder().id(2L).name("JupyterLab").defaultPort(8888)
                .packageInfo(PackageInfoDTO.builder().packageName("quay.io/jupyter/datascience-notebook").packageVersion("latest").build()).build();
        when(grants.issue(eq(1L),eq("vm"),eq("default"),any())).thenReturn(
                new ObjectStorageAccessGrantService.IssuedAccess("test-only-grant",List.of(),LocalDateTime.now().plusHours(1)));
        when(tunnels.gatewayUrl()).thenReturn("http://127.0.0.1:18084/applications/object-storage-gateway");
    }

    @Test void generatedNotebookUsesTunnelAndRestartPreservesUserNotebook(){
        var parameters=new DeploymentParameters();
        parameters.setName("jupyter");
        ReflectionTestUtils.invokeMethod(service,"configureJupyterObjectStorage",parameters,request,catalog,history,"vm");
        assertThat(parameters.getEnvironmentVariables()).containsEntry("MCMP_OBJECT_STORAGE_GATEWAY_URL",
                "http://127.0.0.1:18084/applications/object-storage-gateway");
        assertThat(String.join(" ",parameters.getCommandArguments()))
                .contains("if [ ! -e /home/jovyan/work/sample-data.ipynb ]; then")
                .contains("--ServerApp.default_url=/lab/tree/sample-data.ipynb");
    }

    void runtimeReady(){
        var vm=new VmAccessInfo();vm.setPublicIP("192.0.2.10");
        when(tumblebug.getVmInfo("default","infra","vm")).thenReturn(vm);
        when(docker.runDockerContainer(eq(target),anyMap(),anyMap(),anyString(),anyList(),eq(List.of()),eq(-1)))
                .thenReturn(new ContainerDeployResult(cid,"ok",true));
        when(docker.isContainerRunning(target,cid)).thenReturn(true);
    }

    @Test void verifiesTunnelBeforeOpeningBrowserIngressAndDeclaringSuccess(){
        runtimeReady();
        Object result=ReflectionTestUtils.invokeMethod(service,"deployToSingleVmAsync",
                request,catalog,history,null,"vm",0,List.of("vm"),null);
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(result,"isSuccess")).isTrue();
        var order=inOrder(tunnels,exposure);
        order.verify(tunnels).gatewayUrl();
        order.verify(tunnels).install(1L,target,cid);
        order.verify(exposure).addRestrictedInboundRule(any(),any(),eq(history));
    }

    @Test void tunnelFailureRollsBackParentAndGrantWithoutOpeningIngress(){
        runtimeReady();
        doThrow(new IllegalStateException("tunnel unavailable")).when(tunnels).install(1L,target,cid);
        Object result=ReflectionTestUtils.invokeMethod(service,"deployToSingleVmAsync",
                request,catalog,history,null,"vm",0,List.of("vm"),null);
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(result,"isSuccess")).isFalse();
        verify(docker).removeDockerContainer(target,cid);
        verify(grants).revoke(1L,"vm");
        verify(exposure,never()).addRestrictedInboundRule(any(),any(),any());
        verify(tunnels,atLeastOnce()).remove(1L);
    }

    @Test void imageFailurePreservesCauseAndDoesNotOpenIngressOrInstallTunnel(){
        var vm=new VmAccessInfo();vm.setPublicIP("192.0.2.10");
        when(tumblebug.getVmInfo("default","infra","vm")).thenReturn(vm);
        when(docker.runDockerContainer(eq(target),anyMap(),anyMap(),anyString(),anyList(),eq(List.of()),eq(-1)))
                .thenReturn(new ContainerDeployResult(null,"VM image preparation failed: image pull timed out",false));
        Object result=ReflectionTestUtils.invokeMethod(service,"deployToSingleVmAsync",
                request,catalog,history,null,"vm",0,List.of("vm"),null);
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(result,"isSuccess")).isFalse();
        assertThat((String)ReflectionTestUtils.invokeMethod(result,"getErrorMessage"))
                .contains("image preparation", "image pull timed out");
        verify(tunnels,never()).install(any(),any(),any());
        verify(exposure,never()).addRestrictedInboundRule(any(),any(),any());
        verify(grants).revoke(1L,"vm");
    }
}
