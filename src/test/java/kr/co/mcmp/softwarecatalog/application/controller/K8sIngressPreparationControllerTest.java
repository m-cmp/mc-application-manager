package kr.co.mcmp.softwarecatalog.application.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.K8sIngressPreparationJobs;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class K8sIngressPreparationControllerTest {
    final ProjectScopeAuthorizationService scope=mock(ProjectScopeAuthorizationService.class);
    final K8sIngressPreparationJobs jobs=mock(K8sIngressPreparationJobs.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new K8sIngressPreparationController(scope,jobs)).build();
    static final String URL="/applications/k8s/ingress/preparations";
    static final String INPUT="{\"namespace\":\"project-a\",\"clusterName\":\"cluster-a\",\"catalogId\":7}";
    @Test void bothSubmissionAndPollingAuthorizeTheProjectFirst() throws Exception {
        var status=new K8sIngressPreparationJobs.Status("job","project-a","cluster-a","RUNNING","Preparing");
        when(jobs.start(any())).thenReturn(status); when(jobs.get("project-a","job")).thenReturn(status);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("job"));
        mvc.perform(get(URL+"/job").param("namespace","project-a")).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("RUNNING"));
        var order=inOrder(scope,jobs);
        order.verify(scope).authorizeNamespace(any(),eq("project-a")); order.verify(jobs).start(any());
        order.verify(scope).authorizeNamespace(any(),eq("project-a")); order.verify(jobs).get("project-a","job");
    }
    @Test void unprivilegedCallerCanNeitherStartNorReadJobs() throws Exception {
        when(scope.authorizeNamespace(any(),any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isForbidden());
        mvc.perform(get(URL+"/job").param("namespace","project-a")).andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
    @Test void invalidInputFailsBeforeAuthorizationOrCloudWork() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(URL+"/job")).andExpect(status().isBadRequest());
        verifyNoInteractions(scope,jobs);
    }
}
