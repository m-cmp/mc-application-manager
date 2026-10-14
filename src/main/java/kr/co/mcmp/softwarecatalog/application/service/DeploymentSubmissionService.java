package kr.co.mcmp.softwarecatalog.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequestDTO;

/** Durable receipt/status only: deployment credentials and request bodies are never persisted here. */
@Service
public class DeploymentSubmissionService {
    public record Scope(String workspace, String project, String namespace, String owner) {}
    public record Status(String id, String namespace, String state, String message, Long deploymentId) {}
    private final JdbcTemplate jdbc;
    private final ApplicationOrchestrationService deployments;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    @org.springframework.beans.factory.annotation.Autowired
    public DeploymentSubmissionService(JdbcTemplate jdbc, ApplicationOrchestrationService deployments, ObjectMapper mapper) {
        this(jdbc, deployments, mapper, new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy()));
    }
    DeploymentSubmissionService(JdbcTemplate jdbc, ApplicationOrchestrationService deployments, ObjectMapper mapper, ExecutorService executor) {
        this.jdbc=jdbc; this.deployments=deployments; this.mapper=mapper; this.executor=executor;
    }
    @PostConstruct
    void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS application_deployment_submission (
                id VARCHAR(36) PRIMARY KEY, workspace_id VARCHAR(255) NOT NULL,
                project_id VARCHAR(255) NOT NULL, namespace VARCHAR(255) NOT NULL,
                owner_id VARCHAR(255) NOT NULL, request_hash VARCHAR(64) NOT NULL,
                active_key VARCHAR(64) UNIQUE, target_type VARCHAR(16) NOT NULL, target_id VARCHAR(255) NOT NULL,
                catalog_id BIGINT NOT NULL, automatic_reconciliation BOOLEAN NOT NULL, history_watermark BIGINT NOT NULL, state VARCHAR(32) NOT NULL,
                message VARCHAR(512) NOT NULL, deployment_id BIGINT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.update("UPDATE application_deployment_submission SET state='FAILED', active_key=NULL, message=?, updated_at=CURRENT_TIMESTAMP WHERE state='QUEUED'",
                "AM restarted before this installation started. No deployment was submitted; retry if needed.");
        // Never replay an installation after restart: remote work might already have completed.
        jdbc.update("UPDATE application_deployment_submission SET state='INTERRUPTED', message=?, updated_at=CURRENT_TIMESTAMP WHERE state='RUNNING'",
                "AM restarted while tracking this deployment. Check Apps Status before retrying; no automatic resubmission was made.");
    }
    public Status submit(Scope scope, String id, DeploymentRequestDTO input) {
        try { UUID.fromString(id); } catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A UUID Idempotency-Key is required."); }
        if (input.getCatalogId()==null || input.getDeploymentType()==null || !scope.namespace().equals(input.getNamespace()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment catalog, type and namespace are required.");
        String target=input.getDeploymentType().name().equals("VM") ? input.getMciId() : input.getClusterName();
        if (target==null || target.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Deployment target is required.");
        DeploymentRequestDTO request=mapper.convertValue(input,DeploymentRequestDTO.class);
        String hash=hash(request);
        var prior=jdbc.queryForList("SELECT request_hash FROM application_deployment_submission WHERE id=?",id);
        if (!prior.isEmpty()) {
            Status existing=get(scope,id);
            if (!hash.equals(prior.get(0).get("request_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"This operation ID was used with different deployment settings.");
            return existing;
        }
        // Conservatively serialize the same catalog on an entire VM MCI (including overlapping node groups).
        String active=hash(List.of(scope.namespace(),input.getDeploymentType().name(),target,input.getCatalogId()));
        try {
            jdbc.update("INSERT INTO application_deployment_submission(id,workspace_id,project_id,namespace,owner_id,request_hash,active_key,target_type,target_id,catalog_id,automatic_reconciliation,history_watermark,state,message) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'QUEUED',?)",
                    id,scope.workspace(),scope.project(),scope.namespace(),scope.owner(),hash,active,input.getDeploymentType().name(),target,input.getCatalogId(),input.getDeploymentType().name().equals("K8S") || ((input.getVmNodeGroupId()==null || input.getVmNodeGroupId().isBlank()) && input.getVmIds()!=null && input.getVmIds().size()==1),
                    jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM deployment_history",Long.class),"Deployment accepted; waiting for a worker.");
        } catch (DuplicateKeyException ex) {
            var same=jdbc.queryForList("SELECT id FROM application_deployment_submission WHERE id=?",id);
            if (!same.isEmpty()) return submit(scope,id,input);
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An installation of this catalog on this target is already active or requires reconciliation. Check Apps Status before retrying.");
        }
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        try {
            executor.execute(() -> {
                SecurityContextHolder.setContext(context);
                try { run(id,request); } finally { SecurityContextHolder.clearContext(); }
            });
        } catch (RejectedExecutionException ex) {
            finish(id,"FAILED","Deployment queue is full; no installation was started.",null);
        }
        return get(scope,id);
    }
    public Status get(Scope scope,String id) {
        var found=jdbc.query("SELECT id,namespace,state,message,deployment_id FROM application_deployment_submission WHERE id=? AND workspace_id=? AND project_id=? AND namespace=? AND owner_id=?",
                (rs,n)->new Status(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),(Long)rs.getObject(5)),
                id,scope.workspace(),scope.project(),scope.namespace(),scope.owner());
        if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment operation was not found in this user/project scope.");
        Status status=found.get(0);
        if ("INTERRUPTED".equals(status.state())) {
            // A single matching persisted history can reconcile a lost response. Ambiguous/multi-VM
            // histories are deliberately not guessed; an operator must inspect those deployments.
            var rows=jdbc.queryForList("""
                SELECT h.id,h.status FROM deployment_history h JOIN application_deployment_submission j
                ON h.namespace=j.namespace AND h.catalog_id=j.catalog_id AND h.deployment_type=j.target_type
                AND ((j.target_type='VM' AND h.mci_id=j.target_id) OR (j.target_type='K8S' AND h.cluster_name=j.target_id))
                WHERE j.id=? AND j.automatic_reconciliation=true AND h.id>j.history_watermark AND h.action_type='INSTALL'
                """,id);
            if(rows.size()==1) {
                String recorded=String.valueOf(rows.get(0).get("status"));
                if(Set.of("SUCCESS","FAILED","PARTIAL_SUCCESS","DELETED").contains(recorded)) {
                    Long historyId=((Number)rows.get(0).get("id")).longValue();
                    String reconciled="SUCCESS".equals(recorded)?"SUCCEEDED":("DELETED".equals(recorded)?"FAILED":recorded);
                    finish(id,reconciled,"Tracking recovered from persisted deployment history. Check Apps Status for the current application state.",historyId);
                    return new Status(id,scope.namespace(),reconciled,"Tracking recovered from deployment history.",historyId);
                }
            }
        }
        return status;
    }
    /** Explicit owner action closes tracking only; never changes or retries the real deployment. */
    public Status closeInterrupted(Scope scope,String id) {
        Status status=get(scope,id);
        if(!"INTERRUPTED".equals(status.state())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only interrupted tracking can be closed.");
        int changed=jdbc.update("UPDATE application_deployment_submission SET state='ABANDONED',active_key=NULL,message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND state='INTERRUPTED'",
                "Interrupted tracking was closed by its owner. This does not cancel or change the actual application.",id);
        if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Tracking state changed; reload its status.");
        return get(scope,id);
    }
    private void run(String id,DeploymentRequestDTO request) {
        if(jdbc.update("UPDATE application_deployment_submission SET state='RUNNING',message='Installation is running. Closing the dialog does not cancel it.',updated_at=CURRENT_TIMESTAMP WHERE id=? AND state='QUEUED'",id)!=1) return;
        try {
            var result=deployments.deployApplication(request.toDeploymentRequest());
            String state=result==null ? "FAILED" : result.getStatus();
            Long deploymentId=result==null ? null : result.getId();
            if("SUCCESS".equals(state)) finish(id,"SUCCEEDED","Installation completed.",deploymentId);
            else if("PARTIAL_SUCCESS".equals(state)) finish(id,"PARTIAL_SUCCESS","Only some targets completed. Open Apps Status for individual results.",deploymentId);
            else finish(id,"FAILED","Installation did not complete successfully. Open Apps Status for deployment logs.",deploymentId);
        } catch(Exception ex) {
            // Provider/Helm errors can contain credentials; details remain in existing deployment logging.
            finish(id,"FAILED",DeploymentFailureException.publicReason(ex),
                    ex instanceof DeploymentFailureException failure ? failure.getDeploymentId() : null);
        }
    }
    private void finish(String id,String state,String message,Long deploymentId) {
        jdbc.update("UPDATE application_deployment_submission SET state=?,message=?,deployment_id=?,active_key=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",state,message,deploymentId,id);
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalArgumentException("Deployment settings could not be encoded."); }
    }
    @PreDestroy void shutdown() { executor.shutdown(); }
}
