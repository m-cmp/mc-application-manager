package kr.co.mcmp.softwarecatalog.application.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kr.co.mcmp.exception.GlobalExceptionHandler;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NhnCinderAddonControllerTest {
    private final ProjectScopeAuthorizationService authorization = mock(ProjectScopeAuthorizationService.class);
    private final NhnCinderAddonService service = mock(NhnCinderAddonService.class);
    private final NhnCinderAddonJobs jobs = mock(NhnCinderAddonJobs.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NhnCinderAddonController(authorization, service, jobs))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private static final String URL = "/applications/k8s/nhn-cinder-addon";

    @Test void installationAuthorizesProjectBeforeStartingAsyncJob() throws Exception {
        when(jobs.start("project-a", "cluster-a")).thenReturn(new NhnCinderAddonJobs.Status("job-1", "project-a", "cluster-a", "QUEUED", "", "Waiting"));
        mvc.perform(post(URL).param("namespace", "project-a").param("clusterName", "cluster-a"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.state").value("QUEUED"))
                .andExpect(jsonPath("$.data.credential").doesNotExist());
        var order = inOrder(authorization, jobs); order.verify(authorization).authorizeNamespace(any(), eq("project-a"));
        order.verify(jobs).start("project-a", "cluster-a");
    }
    @Test void allThreeRoutesDenyOtherProjectsBeforeAnyCloudAccess() throws Exception {
        when(authorization.authorizeNamespace(any(), eq("other"))).thenThrow(kr.co.mcmp.security.project.ProjectScopeException.forbidden("Another project"));
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] { get(URL), post(URL), get(URL + "/jobs/job-1") })
            mvc.perform(request.param("namespace", "other").param("clusterName", "cluster-a")).andExpect(status().isForbidden());
        verifyNoInteractions(service, jobs);
    }
    @Test void capabilityOnlyReturnsSafeReadinessInformation() throws Exception {
        when(service.capability("project-a", "cluster-a")).thenReturn(new NhnCinderAddonService.Capability(true, true, false, "AVAILABLE", "v1.30.0", "Install Cinder"));
        mvc.perform(get(URL).param("namespace", "project-a").param("clusterName", "cluster-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canInstall").value(true)).andExpect(jsonPath("$.data.token").doesNotExist());
    }
    @Test void jobLookupRequiresBothNamespaceAndCluster() throws Exception {
        when(jobs.get("project-a", "cluster-a", "job-1")).thenReturn(new NhnCinderAddonJobs.Status("job-1", "project-a", "cluster-a", "READY", "", "Ready"));
        mvc.perform(get(URL + "/jobs/job-1").param("namespace", "project-a").param("clusterName", "cluster-a")).andExpect(status().isOk());
        verify(jobs).get("project-a", "cluster-a", "job-1");
    }
    @Test void providerErrorsUseExistingStorageErrorContract() throws Exception {
        when(jobs.start("project-a", "cluster-a")).thenThrow(new StorageOperationException(400, "NHN_ONLY", "NHN only"));
        mvc.perform(post(URL).param("namespace", "project-a").param("clusterName", "cluster-a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("NHN_ONLY"));
    }
}
