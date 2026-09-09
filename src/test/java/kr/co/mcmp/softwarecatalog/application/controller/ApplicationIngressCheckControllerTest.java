package kr.co.mcmp.softwarecatalog.application.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckResult;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationOrchestrationService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.KubernetesIngressPreflightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ApplicationIngressCheckControllerTest {
    private final ProjectScopeAuthorizationService scope = mock(ProjectScopeAuthorizationService.class);
    private final KubernetesIngressPreflightService ingress = mock(KubernetesIngressPreflightService.class);
    private final ApplicationOrchestrationService orchestration = mock(ApplicationOrchestrationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApplicationController(
            null, orchestration, null, null, null, scope, ingress)).build();

    @Test void checksProjectAuthorizationBeforePreflightAndReturnsBlockingErrorsSeparately() throws Exception {
        when(ingress.check(any())).thenReturn(new K8sIngressCheckResult(false, List.of("Host/Path conflict"), List.of()));
        mvc.perform(post("/applications/k8s/ingress/check").contentType(MediaType.APPLICATION_JSON).content("""
                {"namespace":"project-a","clusterName":"cluster-a","catalogId":7,
                 "ingressEnabled":true,"ingressHost":"app.example.com","ingressPath":"/app"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value("Host/Path conflict"))
                .andExpect(jsonPath("$.data.warnings").isEmpty());
        var order = inOrder(scope, ingress);
        order.verify(scope).authorizeNamespace(any(), eq("project-a"));
        order.verify(ingress).check(argThat(r -> "app.example.com".equals(r.getIngressHost()) && "/app".equals(r.getIngressPath())));
        verifyNoInteractions(orchestration);
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"namespace\":\"\",\"clusterName\":\"c\",\"catalogId\":1}",
            "{\"namespace\":\"project-a\",\"clusterName\":\"\",\"catalogId\":1}",
            "{\"namespace\":\"project-a\",\"clusterName\":\"c\",\"catalogId\":0}"
    })
    void rejectsIncompleteTargetsBeforeAnyClusterLookup(String json) throws Exception {
        mvc.perform(post("/applications/k8s/ingress/check").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ingress, orchestration);
    }

    @Test void anotherProjectCannotUseThisEndpointToReadClusterRoutesOrSecrets() throws Exception {
        when(scope.authorizeNamespace(any(), eq("other-project")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(post("/applications/k8s/ingress/check").contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"other-project\",\"clusterName\":\"c\",\"catalogId\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ingress, orchestration);
    }

    @Test void existingResourceCheckContractStaysBoolean() throws Exception {
        when(orchestration.checkSpecForK8s("project-a", "c", 7L)).thenReturn(false);
        mvc.perform(get("/applications/k8s/check").param("namespace", "project-a").param("clusterName", "c").param("catalogId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
        verifyNoInteractions(ingress);
    }
}
