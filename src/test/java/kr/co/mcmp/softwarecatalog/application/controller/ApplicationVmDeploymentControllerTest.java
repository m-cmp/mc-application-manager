package kr.co.mcmp.softwarecatalog.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;

class ApplicationVmDeploymentControllerTest {

    private ProjectScopeAuthorizationService scope;
    private ApplicationOrchestrationService orchestration;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        scope = mock(ProjectScopeAuthorizationService.class);
        orchestration = mock(ApplicationOrchestrationService.class);
        when(orchestration.deployApplication(any())).thenReturn(DeploymentHistory.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationController(
                null, orchestration, null, null, null, null, scope, null)).build();
    }

    @Test
    void acceptsLegacyVmRequestWithoutNodeGroup() throws Exception {
        DeploymentRequest request = performAndCapture("""
                {"namespace":"project-a","mciId":"mci-a","vmIds":["vm-a"],
                 "catalogId":7,"vmDeploymentMode":"STANDALONE"}
                """);

        assertThat(request.getDeploymentType()).isEqualTo(DeploymentType.VM);
        assertThat(request.getVmIds()).containsExactly("vm-a");
        assertThat(request.getVmNodeGroupId()).isNull();
    }

    @Test
    void passesNodeGroupWithOneVmForMembershipValidation() throws Exception {
        DeploymentRequest request = performAndCapture("""
                {"namespace":"project-a","mciId":"mci-a","vmIds":["vm-a"],
                 "vmNodeGroupId":"group-a","catalogId":7,"vmDeploymentMode":"STANDALONE"}
                """);

        assertThat(request.getVmIds()).containsExactly("vm-a");
        assertThat(request.getVmNodeGroupId()).isEqualTo("group-a");
        assertThat(request.getVmDeploymentMode()).isEqualTo(VmDeploymentMode.STANDALONE);
    }

    @Test
    void passesGroupOnlyRequestWithoutInventingVmIds() throws Exception {
        DeploymentRequest request = performAndCapture("""
                {"namespace":"project-a","mciId":"mci-a","vmIds":[],
                 "vmNodeGroupId":"group-a","catalogId":7,"vmDeploymentMode":"STANDALONE"}
                """);

        assertThat(request.getVmIds()).isEmpty();
        assertThat(request.getVmNodeGroupId()).isEqualTo("group-a");
    }

    private DeploymentRequest performAndCapture(String json) throws Exception {
        mvc.perform(post("/applications/vm/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(scope).authorizeNamespace(any(), org.mockito.ArgumentMatchers.eq("project-a"));
        ArgumentCaptor<DeploymentRequest> requestCaptor = ArgumentCaptor.forClass(DeploymentRequest.class);
        verify(orchestration).deployApplication(requestCaptor.capture());
        return requestCaptor.getValue();
    }
}
