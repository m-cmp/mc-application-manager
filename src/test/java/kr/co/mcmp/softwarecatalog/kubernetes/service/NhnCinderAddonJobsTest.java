package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class NhnCinderAddonJobsTest {
    private final NhnCinderAddonService service = mock(NhnCinderAddonService.class);
    private final NhnCinderAddonService.Target target = new NhnCinderAddonService.Target("project-a", "cluster-a", NhnCinderAddonServiceTest.ID, "nhn-kr1", "kr1");

    @Test void concurrentRequestsForSameClusterReuseOneJobAndOneInstallation() throws Exception {
        when(service.resolve("project-a", "cluster-a")).thenReturn(target);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(a -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; }).when(service).prepare(eq(target), any());
        var jobs = new NhnCinderAddonJobs(service);
        try {
            var first = jobs.start("project-a", "cluster-a"); assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            var second = jobs.start("project-a", "cluster-a"); assertThat(second.id()).isEqualTo(first.id());
            assertThatThrownBy(() -> jobs.get("other", "cluster-a", first.id())).hasMessageContaining("not found");
            assertThatThrownBy(() -> jobs.get("project-a", "other", first.id())).hasMessageContaining("not found");
            release.countDown();
            var result = terminal(jobs, first.id()); assertThat(result.state()).isEqualTo("READY");
            verify(service, times(1)).prepare(eq(target), any());
        } finally { release.countDown(); jobs.shutdown(); }
    }
    @Test void unclassifiedFailureCannotExposeCredentialOrUpstreamPayload() throws Exception {
        when(service.resolve("project-a", "cluster-a")).thenReturn(target);
        doThrow(new IllegalStateException("secret-password-response-body")).when(service).prepare(eq(target), any());
        var jobs = new NhnCinderAddonJobs(service);
        try {
            var status = terminal(jobs, jobs.start("project-a", "cluster-a").id());
            assertThat(status.state()).isEqualTo("FAILED"); assertThat(status.message()).doesNotContain("secret-password");
            assertThat(status.code()).isEqualTo("NHN_ADDON_PREPARATION_FAILED");
        } finally { jobs.shutdown(); }
    }
    @Test void safeProviderFailurePreservesActionableErrorCode() throws Exception {
        when(service.resolve("project-a", "cluster-a")).thenReturn(target);
        doThrow(new StorageOperationException(502, "NHN_ADDON_FORBIDDEN", "Check NHN permissions.")).when(service).prepare(eq(target), any());
        var jobs = new NhnCinderAddonJobs(service);
        try { assertThat(terminal(jobs, jobs.start("project-a", "cluster-a").id()).code()).isEqualTo("NHN_ADDON_FORBIDDEN"); }
        finally { jobs.shutdown(); }
    }
    private static NhnCinderAddonJobs.Status terminal(NhnCinderAddonJobs jobs, String id) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            var status = jobs.get("project-a", "cluster-a", id);
            if (status.state().equals("READY") || status.state().equals("FAILED")) return status;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Local job did not terminate");
    }
}
