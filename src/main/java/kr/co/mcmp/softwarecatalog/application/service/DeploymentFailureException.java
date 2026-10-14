package kr.co.mcmp.softwarecatalog.application.service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Public failure receipt: never expose arbitrary provider exception text or credentials. */
public class DeploymentFailureException extends RuntimeException {
    private final Long deploymentId;
    public DeploymentFailureException(Long deploymentId, Throwable cause) {
        super(publicReason(cause), cause);
        this.deploymentId = deploymentId;
    }
    public Long getDeploymentId() { return deploymentId; }
    public static String publicReason(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof DeploymentFailureException) return cause.getMessage();
            String message = String.valueOf(cause.getMessage());
            if (Set.of("Enable Ingress to configure external CIDR access.",
                    "Enter an Ingress hostname for external access.",
                    "Ingress hostname is already in use.").contains(message)) return message;
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("imagepullbackoff") || lower.contains("errimagepull"))
                return "The container image could not be pulled. Open deployment logs for details.";
            if (lower.contains("timed out") || lower.contains("timeout"))
                return "Deployment verification timed out. Check deployment logs and the current workload before retrying.";
            if (lower.contains("forbidden") || lower.contains("unauthorized"))
                return "Cluster or registry access was denied. Open deployment logs for details.";
            if (lower.contains("storageclass") || lower.contains("persistentvolumeclaim"))
                return "Storage preparation failed. Open deployment logs for details.";
        }
        return "Installation failed. Open deployment logs for details.";
    }
}
