package kr.co.mcmp.softwarecatalog.application.service.tunnel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import kr.co.mcmp.softwarecatalog.application.model.K8sObjectStorageTunnel;
import kr.co.mcmp.softwarecatalog.application.repository.K8sObjectStorageTunnelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Separate lifecycle from VM tunnels, with a durable lease for AM restarts/replicas. */
@Service @RequiredArgsConstructor @Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class K8sObjectStorageTunnelService {
    static final long LEASE_SECONDS = 120;
    private final K8sObjectStorageTunnelRepository repository;
    private final K8sObjectStorageTunnelRuntime runtime;
    private final String owner = UUID.randomUUID().toString();
    private final ConcurrentMap<Long,K8sObjectStorageTunnelRuntime.Running> running = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile Instant lastRenewed = Instant.now();
    private ScheduledExecutorService scheduler;
    @Value("${app.scheduling.enabled:true}") private boolean reconcileEnabled = true;

    public Secret credentials(String namespace, String name) { return runtime.credentials(namespace, name); }

    public synchronized void register(Long id, String projectNamespace, String cluster, Deployment deployment, Secret secret) {
        if (closed) throw new IllegalStateException("AM is shutting down");
        if (repository.existsById(id)) throw new IllegalStateException("Tunnel already registered for this deployment");
        var t = new K8sObjectStorageTunnel();
        t.setDeploymentId(id); t.setNamespace(projectNamespace); t.setClusterName(cluster);
        t.setReleaseName(deployment.getMetadata().getName()); t.setWorkloadUid(deployment.getMetadata().getUid());
        t.setSecretUid(secret.getMetadata().getUid()); t.setLeaseOwner(owner);
        t.setLeaseUntil(Instant.now().plusSeconds(LEASE_SECONDS)); t.setUpdatedAt(Instant.now());
        repository.saveAndFlush(t);
        // Pods may still be pulling images. The install loop calls ensure until ready.
    }

    public synchronized void ensure(Long id) {
        if (closed) throw new IllegalStateException("AM is shutting down");
        repository.findById(id).ifPresent(this::reconcileOne);
    }

    public synchronized void remove(Long id) { change(id, "DELETED"); }
    public synchronized void suspend(Long id) { change(id, "STOPPED"); }
    public synchronized void resume(Long id) { change(id, "ACTIVE"); }
    public boolean exists(Long id) { return repository.existsById(id); }

    private void change(Long id, String state) {
        repository.findById(id).ifPresent(t -> {
            if ("DELETED".equals(t.getDesiredState())) return;
            repository.desire(id, state, Instant.now());
            if (!"ACTIVE".equals(state)) stopLocal(id);
            t.setDesiredState(state);
            // A different AM owner observes this desired state on its next tick.
            if (!"ACTIVE".equals(state)) repository.status(id, owner, state, Instant.now());
        });
    }

    private void reconcileOne(K8sObjectStorageTunnel t) {
        Long id = t.getDeploymentId();
        Instant now = Instant.now();
        if (repository.claim(id, owner, now, now.plusSeconds(LEASE_SECONDS)) != 1) { stopLocal(id); return; }
        t = repository.findById(id).orElseThrow();
        if (!"ACTIVE".equals(t.getDesiredState())) { stopLocal(id); return; }
        try {
            var active = running.get(id);
            if (active == null || !active.alive() || !active.currentPod()) {
                stopLocal(id);
                active = runtime.start(t);
                running.put(id, active);
            }
            // Startup may still be authenticating. Pod readiness verifies the reverse path.
            repository.status(id, owner, active.ready() ? "READY" : "CONNECTING", Instant.now());
            if (!"ACTIVE".equals(repository.findById(id).orElseThrow().getDesiredState())) stopLocal(id);
        } catch (RuntimeException failure) {
            stopLocal(id);
            repository.status(id, owner, "RETRYING", Instant.now());
            throw failure;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        scheduler = Executors.newScheduledThreadPool(2, task -> {
            var thread = new Thread(task, "k8s-object-storage-tunnel"); thread.setDaemon(true); return thread;
        });
        scheduler.scheduleWithFixedDelay(this::heartbeat, 15, 15, TimeUnit.SECONDS);
        // Local AM only maintains explicitly registered/owned tunnels when scheduling is disabled.
        scheduler.scheduleWithFixedDelay(this::reconcile, 5, 10, TimeUnit.SECONDS);
    }

    void heartbeat() {
        if (closed || running.isEmpty()) return;
        Instant now = Instant.now();
        try {
            if (repository.renew(owner, now, now.plusSeconds(LEASE_SECONDS)) == 0) running.keySet().forEach(this::stopLocal);
            lastRenewed = now;
        } catch (RuntimeException e) {
            if (lastRenewed.plusSeconds(LEASE_SECONDS - 15).isBefore(now)) running.keySet().forEach(this::stopLocal);
        }
    }

    synchronized void reconcile() {
        if (closed) return;
        try {
            // Stop/deleted records must also be read, so another AM can revoke a live connection.
            for (var t : repository.findAll()) {
                if (!"ACTIVE".equals(t.getDesiredState())) { stopLocal(t.getDeploymentId()); continue; }
                if (!reconcileEnabled && !owner.equals(t.getLeaseOwner())) continue;
                try { reconcileOne(t); }
                catch (RuntimeException e) { log.debug("K8s tunnel will retry deployment {}", t.getDeploymentId()); }
            }
        } catch (RuntimeException e) { log.warn("K8s tunnel reconciliation deferred; database unavailable"); }
    }

    private void stopLocal(Long id) { var active = running.remove(id); if (active != null) active.close(); }
    @PreDestroy public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
        running.keySet().forEach(this::stopLocal);
        try { repository.release(owner); } catch (RuntimeException ignored) { }
    }
}
