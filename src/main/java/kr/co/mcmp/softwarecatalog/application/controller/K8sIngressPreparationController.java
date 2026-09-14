package kr.co.mcmp.softwarecatalog.application.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.kubernetes.service.K8sIngressPreparationJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/applications/k8s/ingress/preparations") @RequiredArgsConstructor
public class K8sIngressPreparationController {
    private final ProjectScopeAuthorizationService authorization;
    private final K8sIngressPreparationJobs jobs;
    @PostMapping
    public ResponseWrapper<K8sIngressPreparationJobs.Status> start(@Valid @RequestBody K8sIngressCheckRequest request, HttpServletRequest http) {
        authorization.authorizeNamespace(http,request.getNamespace());
        return new ResponseWrapper<>(jobs.start(request));
    }
    @GetMapping("/{id}")
    public ResponseWrapper<K8sIngressPreparationJobs.Status> get(@PathVariable String id, @RequestParam String namespace, HttpServletRequest http) {
        authorization.authorizeNamespace(http,namespace);
        return new ResponseWrapper<>(jobs.get(namespace,id));
    }
}
