package kr.co.mcmp.softwarecatalog.application.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.kubernetes.service.NhnCinderAddonJobs;
import kr.co.mcmp.softwarecatalog.kubernetes.service.NhnCinderAddonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/applications/k8s/nhn-cinder-addon")
@RequiredArgsConstructor
public class NhnCinderAddonController {
    private final ProjectScopeAuthorizationService authorization;
    private final NhnCinderAddonService service;
    private final NhnCinderAddonJobs jobs;

    @GetMapping
    public ResponseWrapper<NhnCinderAddonService.Capability> capability(@RequestParam String namespace,
            @RequestParam String clusterName, HttpServletRequest http) {
        authorization.authorizeNamespace(http, namespace);
        return new ResponseWrapper<>(service.capability(namespace, clusterName));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseWrapper<NhnCinderAddonJobs.Status> start(@RequestParam String namespace,
            @RequestParam String clusterName, HttpServletRequest http) {
        authorization.authorizeNamespace(http, namespace);
        return new ResponseWrapper<>(jobs.start(namespace, clusterName));
    }
    @GetMapping("/jobs/{id}")
    public ResponseWrapper<NhnCinderAddonJobs.Status> get(@PathVariable String id, @RequestParam String namespace,
            @RequestParam String clusterName, HttpServletRequest http) {
        authorization.authorizeNamespace(http, namespace);
        return new ResponseWrapper<>(jobs.get(namespace, clusterName, id));
    }
}
