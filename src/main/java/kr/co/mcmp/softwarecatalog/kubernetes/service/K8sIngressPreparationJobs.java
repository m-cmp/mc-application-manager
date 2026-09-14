package kr.co.mcmp.softwarecatalog.kubernetes.service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;
import org.springframework.stereotype.Service;

/** Bounded asynchronous preparation avoids HTTP/proxy timeouts during LB recreation and ACME validation. */
@Service
public class K8sIngressPreparationJobs {
    public record Status(String id, String namespace, String clusterName, String state, String message) { }
    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final K8sIngressCheckRequest request;
        volatile String state = "QUEUED", message = "Waiting to prepare IBM Ingress…";
        volatile Instant completed;
        Job(K8sIngressCheckRequest request) {
            // Keep the asynchronous identity immutable even if an internal caller reuses its DTO.
            this.request = new K8sIngressCheckRequest();
            org.springframework.beans.BeanUtils.copyProperties(request,this.request);
        }
        Status status() { return new Status(id, request.getNamespace(), request.getClusterName(), state, message); }
    }
    private final Map<String,Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),
            runnable -> { var thread = new Thread(runnable,"am-ingress-preparation"); thread.setDaemon(true); return thread; },new ThreadPoolExecutor.AbortPolicy());
    private final KubernetesIngressPreflightService preflight;
    private final KubernetesClientFactory clients;
    private final CatalogRepository catalogs;
    private final K8sIngressAccessService access;
    private final IbmIngressAutomationService automation;

    public K8sIngressPreparationJobs(KubernetesIngressPreflightService preflight, KubernetesClientFactory clients,
                                    CatalogRepository catalogs, K8sIngressAccessService access, IbmIngressAutomationService automation) {
        this.preflight=preflight; this.clients=clients; this.catalogs=catalogs; this.access=access; this.automation=automation;
    }
    public synchronized Status start(K8sIngressCheckRequest request) {
        jobs.values().removeIf(job -> job.completed != null && job.completed.isBefore(Instant.now().minusSeconds(7200)));
        for (var job : jobs.values()) {
            if (job.completed == null && job.request.getNamespace().equals(request.getNamespace())
                    && job.request.getClusterName().equals(request.getClusterName())) {
                if (job.request.equals(request)) return job.status();
                throw new IllegalArgumentException("Another Ingress preparation is already running for this cluster.");
            }
        }
        if (jobs.size() >= 100) throw new IllegalArgumentException("Ingress preparation capacity is full; retry later.");
        Job job = new Job(request); jobs.put(job.id,job);
        try { executor.execute(() -> run(job)); }
        catch (RejectedExecutionException e) { jobs.remove(job.id); throw new IllegalArgumentException("Ingress preparation queue is full; retry later."); }
        return job.status();
    }
    public Status get(String namespace, String id) {
        var job = jobs.get(id);
        if (job == null || !job.request.getNamespace().equals(namespace))
            throw new IllegalArgumentException("Preparation not found. If AM restarted, retry Deploy to check and reuse existing cluster resources.");
        return job.status();
    }
    private void run(Job job) {
        job.state="RUNNING";
        try {
            job.message="Checking permissions, domain configuration and existing Ingress routes…";
            var check = preflight.check(job.request);
            if (!check.valid()) throw new IllegalArgumentException(String.join(" ",check.errors()));
            var catalog = catalogs.findById(job.request.getCatalogId()).orElseThrow(() -> new IllegalArgumentException("Software catalog not found."));
            var target = job.request.toDeploymentRequest(); access.resolveTarget(target,catalog);
            var config = DeploymentConfigDTO.from(target,catalog);
            if (!config.isIngressEnabled() || !IbmIngressSupport.managed(config.getIngressClass()))
                throw new IllegalArgumentException("This preparation endpoint requires IBM managed Ingress.");
            String workload = catalog.getPackageInfo() != null && catalog.getPackageInfo().getPackageName() != null
                    && catalog.getPackageInfo().getPackageName().toLowerCase(Locale.ROOT).contains("jupyter")
                    ? target.getNamespace() : KubernetesNamespaces.APPLICATION_WORKLOAD;
            try (var client = clients.getClient(target.getNamespace(),target.getClusterName())) {
                automation.prepare(client,target.getNamespace(),target.getClusterName(),workload,config, message -> job.message=message);
            }
            job.message="Ingress preparation completed. Ready to deploy the application."; job.state="READY";
        } catch (IllegalArgumentException e) { job.message=e.getMessage(); job.state="FAILED"; }
        catch (Exception e) {
            // K8s/client/parser errors may include Secret bodies. Never relay them through status responses.
            job.message="Ingress preparation could not complete. Check cluster permissions, Helm/cert-manager and IBM status. Existing shared resources were not automatically rolled back.";
            job.state="FAILED";
        } finally { job.completed=Instant.now(); }
    }
    @PreDestroy void shutdown() { executor.shutdownNow(); }
}
