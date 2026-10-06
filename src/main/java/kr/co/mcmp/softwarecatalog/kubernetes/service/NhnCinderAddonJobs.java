package kr.co.mcmp.softwarecatalog.kubernetes.service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

/** One installation job per project/cluster, bounded queue, no credentials in job records. */
@Service
public class NhnCinderAddonJobs {
    public record Status(String id, String namespace, String clusterName, String state, String code, String message) { }
    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final NhnCinderAddonService.Target target;
        volatile String state = "QUEUED", code = "", message = "Waiting to prepare Cinder CSI…";
        volatile Instant completed;
        Job(NhnCinderAddonService.Target target) { this.target = target; }
        synchronized Status status() { return new Status(id, target.namespace(), target.clusterName(), state, code, message); }
        synchronized void update(String state, String code, String message) { this.state = state; this.code = code; this.message = message; }
    }
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4),
            runnable -> { var thread = new Thread(runnable, "am-nhn-cinder-addon"); thread.setDaemon(true); return thread; }, new ThreadPoolExecutor.AbortPolicy());
    private final NhnCinderAddonService service;
    public NhnCinderAddonJobs(NhnCinderAddonService service) { this.service = service; }

    public synchronized Status start(String namespace, String clusterName) {
        var target = service.resolve(namespace, clusterName);
        jobs.values().removeIf(job -> job.completed != null && job.completed.isBefore(Instant.now().minusSeconds(7200)));
        for (var job : jobs.values()) {
            if (job.completed == null && job.target.namespace().equals(namespace) && job.target.clusterName().equals(clusterName)) {
                if (!job.target.equals(target)) throw new StorageOperationException(409, "NHN_CLUSTER_CHANGED", "The selected cluster changed while a Cinder job was running. Refresh status before retrying.");
                return job.status();
            }
        }
        if (jobs.size() >= 100) throw capacity();
        Job job = new Job(target); jobs.put(job.id, job);
        try { executor.execute(() -> run(job)); }
        catch (RejectedExecutionException e) { jobs.remove(job.id); throw capacity(); }
        return job.status();
    }
    public Status get(String namespace, String clusterName, String id) {
        var job = jobs.get(id);
        if (job == null || !job.target.namespace().equals(namespace) || !job.target.clusterName().equals(clusterName))
            throw new StorageOperationException(404, "NHN_ADDON_JOB_NOT_FOUND", "Cinder preparation was not found. If AM restarted, refresh status and retry to reuse existing NKS resources.");
        return job.status();
    }
    private void run(Job job) {
        job.update("RUNNING", "", "Checking Cinder installation and worker readiness…");
        try {
            service.prepare(job.target, message -> job.update("RUNNING", "", message));
            job.update("READY", "", "Cinder CSI is ready. Select or create an NHN StorageClass.");
        } catch (StorageOperationException e) { job.update("FAILED", e.getCode(), e.getMessage()); }
        catch (Exception e) { job.update("FAILED", "NHN_ADDON_PREPARATION_FAILED", "Cinder preparation could not complete. Refresh status and check AM connectivity and NKS permissions."); }
        finally { job.completed = Instant.now(); }
    }
    private static StorageOperationException capacity() { return new StorageOperationException(429, "NHN_ADDON_QUEUE_FULL", "Cinder preparation capacity is full. Retry later."); }
    @PreDestroy public void shutdown() { executor.shutdownNow(); }
}
