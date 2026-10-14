package kr.co.mcmp.softwarecatalog.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequestDTO;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentSubmissionServiceTest {
    JdbcTemplate jdbc;
    ApplicationOrchestrationService orchestration;
    DeploymentSubmissionService jobs;
    QueueExecutor executor;
    DeploymentSubmissionService.Scope scope = new DeploymentSubmissionService.Scope("w1","p1","default","user1");
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE deployment_history(id BIGINT, namespace VARCHAR, catalog_id BIGINT, deployment_type VARCHAR, mci_id VARCHAR, cluster_name VARCHAR, action_type VARCHAR, status VARCHAR)");
        orchestration=mock(ApplicationOrchestrationService.class); executor=new QueueExecutor();
        jobs=new DeploymentSubmissionService(jdbc,orchestration,new ObjectMapper(),executor); jobs.initialize();
    }
    DeploymentRequestDTO request(DeploymentType type) {
        return DeploymentRequestDTO.builder().namespace("default").catalogId(11L).deploymentType(type)
                .mciId("test-mci").vmIds(List.of("vm1")).clusterName("test-cluster").build();
    }
    @Test void returnsReceiptBeforeWorkAndRetriesSameIdWithoutDuplicateInstall() {
        String id=UUID.randomUUID().toString(); var req=request(DeploymentType.VM);
        when(orchestration.deployApplication(any())).thenReturn(DeploymentHistory.builder().id(22L).status("SUCCESS").build());
        assertThat(jobs.submit(scope,id,req).state()).isEqualTo("QUEUED");
        assertThat(jobs.submit(scope,id,req).id()).isEqualTo(id);
        verifyNoInteractions(orchestration); assertThat(executor.tasks).hasSize(1);
        executor.runNext();
        assertThat(jobs.get(scope,id).state()).isEqualTo("SUCCEEDED");
        assertThat(jobs.get(scope,id).deploymentId()).isEqualTo(22L);
        jobs.submit(scope,id,req); verify(orchestration,times(1)).deployApplication(any());
    }
    @Test void concurrentTargetAndChangedPayloadAreRejected() {
        var req=request(DeploymentType.K8S); String id=UUID.randomUUID().toString();jobs.submit(scope,id,req);
        assertThatThrownBy(()->jobs.submit(scope,UUID.randomUUID().toString(),req)).isInstanceOf(ResponseStatusException.class);
        req.setServicePort(8088);
        assertThatThrownBy(()->jobs.submit(scope,id,req)).isInstanceOf(ResponseStatusException.class);
        assertThat(executor.tasks).hasSize(1);
    }
    @Test void propagatesFailedAndPartialResultsInsteadOfTreatingAnyHistoryAsSuccess() {
        for(String state:List.of("FAILED","PARTIAL_SUCCESS")) {
            when(orchestration.deployApplication(any())).thenReturn(DeploymentHistory.builder().id(23L).status(state).build());
            String id=UUID.randomUUID().toString();jobs.submit(scope,id,request(DeploymentType.K8S));executor.runNext();
            assertThat(jobs.get(scope,id).state()).isEqualTo(state);
        }
    }
    @Test void persistsRestartStateAndRetainsGuardRatherThanReplayingRemoteWork() {
        String id=UUID.randomUUID().toString();jobs.submit(scope,id,request(DeploymentType.VM));
        jdbc.update("UPDATE application_deployment_submission SET state='RUNNING' WHERE id=?",id);
        var restarted=new DeploymentSubmissionService(jdbc,orchestration,new ObjectMapper(),new QueueExecutor());restarted.initialize();
        assertThat(restarted.get(scope,id).state()).isEqualTo("INTERRUPTED");
        assertThatThrownBy(()->restarted.submit(scope,UUID.randomUUID().toString(),request(DeploymentType.VM))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(orchestration);
    }
    @Test void preservesFailureHistoryAndSafeReasonForAuthenticatedOwner() {
        String id = UUID.randomUUID().toString();
        when(orchestration.deployApplication(any())).thenThrow(new DeploymentFailureException(42L,
                new IllegalArgumentException("Enable Ingress to configure external CIDR access.")));
        jobs.submit(scope, id, request(DeploymentType.K8S)); executor.runNext();
        assertThat(jobs.get(scope,id).deploymentId()).isEqualTo(42L);
        assertThat(jobs.get(scope,id).message()).isEqualTo("Enable Ingress to configure external CIDR access.");
        assertThatThrownBy(() -> jobs.get(new DeploymentSubmissionService.Scope("w1","p2","default","user1"),id))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void isolatesOwnerAndProjectAndDoesNotPersistCredentialsOrExceptionText() {
        String id=UUID.randomUUID().toString();var req=request(DeploymentType.VM);req.setAdditionalConfig(Map.of("password","sensitive-test-value"));
        when(orchestration.deployApplication(any())).thenThrow(new IllegalStateException("sensitive-test-value"));
        jobs.submit(scope,id,req);
        assertThatThrownBy(()->jobs.get(new DeploymentSubmissionService.Scope("w1","p1","default","user2"),id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->jobs.get(new DeploymentSubmissionService.Scope("w1","p2","default","user1"),id)).isInstanceOf(ResponseStatusException.class);
        executor.runNext();assertThat(jobs.get(scope,id).state()).isEqualTo("FAILED");
        assertThat(jdbc.queryForList("SELECT * FROM application_deployment_submission").toString()).doesNotContain("sensitive-test-value");
    }
    @Test void restartClosesNeverStartedQueueAndReconcilesOnlyUniqueTerminalHistory() {
        String queued=UUID.randomUUID().toString();jobs.submit(scope,queued,request(DeploymentType.VM));jobs.initialize();
        assertThat(jobs.get(scope,queued).state()).isEqualTo("FAILED");
        String running=UUID.randomUUID().toString();jobs.submit(scope,running,request(DeploymentType.VM));
        jdbc.update("UPDATE application_deployment_submission SET state='RUNNING' WHERE id=?",running);jobs.initialize();
        jdbc.update("INSERT INTO deployment_history VALUES(22,'default',11,'VM','test-mci',NULL,'INSTALL','SUCCESS')");
        assertThat(jobs.get(scope,running).state()).isEqualTo("SUCCEEDED");
        assertThat(jobs.get(scope,running).deploymentId()).isEqualTo(22L);
        verifyNoInteractions(orchestration);
    }
    @Test void returnsWhileDeploymentIsStillBlockedOnRemoteWork() throws Exception {
        var real=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(orchestration.deployApplication(any())).thenAnswer(invocation->{started.countDown();release.await(5,TimeUnit.SECONDS);return DeploymentHistory.builder().id(24L).status("SUCCESS").build();});
        var async=new DeploymentSubmissionService(jdbc,orchestration,new ObjectMapper(),real);
        String id=UUID.randomUUID().toString();
        try {
            var receipt=async.submit(scope,id,request(DeploymentType.K8S));
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(receipt.state()).isIn("QUEUED","RUNNING");
            assertThat(async.get(scope,id).state()).isEqualTo("RUNNING");
        } finally {release.countDown();real.shutdown();assertThat(real.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
        assertThat(async.get(scope,id).state()).isEqualTo("SUCCEEDED");
    }
    @Test void multiVmPartialHistoryCannotReconcileAndOnlyOwnerCanCloseInterruptedTracking() {
        String id=UUID.randomUUID().toString();var req=request(DeploymentType.VM);req.setVmIds(List.of("vm1","vm2"));
        jobs.submit(scope,id,req);jdbc.update("UPDATE application_deployment_submission SET state='RUNNING' WHERE id=?",id);jobs.initialize();
        jdbc.update("INSERT INTO deployment_history VALUES(25,'default',11,'VM','test-mci',NULL,'INSTALL','SUCCESS')");
        assertThat(jobs.get(scope,id).state()).isEqualTo("INTERRUPTED");
        assertThatThrownBy(()->jobs.closeInterrupted(new DeploymentSubmissionService.Scope("w1","p1","default","other"),id)).isInstanceOf(ResponseStatusException.class);
        assertThat(jobs.closeInterrupted(scope,id).state()).isEqualTo("ABANDONED");
        assertThat(jdbc.queryForObject("SELECT status FROM deployment_history WHERE id=25",String.class)).isEqualTo("SUCCESS");
        jobs.submit(scope,UUID.randomUUID().toString(),req);
        verifyNoInteractions(orchestration);
    }
    @Test void runningTrackingCannotBeClosedAndDeletedHistoryDoesNotReportSuccess() {
        String id=UUID.randomUUID().toString();jobs.submit(scope,id,request(DeploymentType.K8S));
        assertThatThrownBy(()->jobs.closeInterrupted(scope,id)).isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE application_deployment_submission SET state='RUNNING' WHERE id=?",id);jobs.initialize();
        jdbc.update("INSERT INTO deployment_history VALUES(26,'default',11,'K8S',NULL,'test-cluster','INSTALL','DELETED')");
        assertThat(jobs.get(scope,id).state()).isEqualTo("FAILED");
    }
    static class QueueExecutor extends AbstractExecutorService {
        List<Runnable> tasks=new ArrayList<>(); public void execute(Runnable task){tasks.add(task);}
        void runNext(){tasks.remove(0).run();}
        public void shutdown(){} public List<Runnable> shutdownNow(){return List.of();}
        public boolean isShutdown(){return false;} public boolean isTerminated(){return false;}
        public boolean awaitTermination(long timeout,TimeUnit unit){return true;}
    }
}
