package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.client.KubernetesClientException;

/** Safe, actionable errors; never return kubeconfig or upstream response bodies. */
public class StorageOperationException extends RuntimeException {
    private final int status;
    private final String code;
    public StorageOperationException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static StorageOperationException translate(RuntimeException failure) {
        if (failure instanceof StorageOperationException e) return e;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof KubernetesClientException e) {
                if (e.getCode() == 401) return new StorageOperationException(401, "K8S_AUTHENTICATION_FAILED", "Kubernetes authentication failed. Refresh the cluster credentials.");
                if (e.getCode() == 403) return new StorageOperationException(403, "K8S_STORAGE_FORBIDDEN", "Kubernetes denied the storage operation. Check AM cluster RBAC permissions.");
                if (e.getCode() == 409) return new StorageOperationException(409, "STORAGE_CLASS_CONFLICT", "A StorageClass with this name already exists. Refresh the list and select it, or use another name.");
            }
        }
        return new StorageOperationException(502, "K8S_STORAGE_API_UNAVAILABLE", "Unable to query the cluster storage API. Check the API endpoint, network access and cluster credentials, then retry.");
    }
}
