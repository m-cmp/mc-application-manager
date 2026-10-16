package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Reuse NKS's preexisting cluster-scoped trust; never copy it to AM configuration or the browser. */
@Component
public class NhnClusterIdentitySessions {
    record TrustCredential(String userId, String password, String trustId) {
        @Override public String toString() { return "NHN cluster trust (credentials withheld)"; }
    }
    private final NhnNksApi nks;
    public NhnClusterIdentitySessions(NhnNksApi nks) { this.nks = nks; }

    NhnNksApi.Session login(KubernetesClient client, String region) {
        requireRegion(region);
        TrustCredential credential;
        try {
            Secret secret = client.secrets().inNamespace("kube-system").withName("os-trustee").get();
            if (secret == null || !"Opaque".equals(secret.getType()) || secret.getData() == null)
                throw new IllegalArgumentException();
            credential = new TrustCredential(value(secret, "os-trusteeID", 256),
                    value(secret, "os-trusteePassword", 8192), value(secret, "os-trustID", 256));
        } catch (Exception e) {
            throw new StorageOperationException(503, "NHN_CLUSTER_IDENTITY_UNAVAILABLE",
                    "AM could not read the selected NKS cluster's os-trustee identity. Check Kubernetes access to kube-system/os-trustee and the NKS cluster identity status.");
        }
        // os-authURL can be an internal HTTP broker and os-region can be RegionOne.
        // Neither is a public API target. Use fixed NHN HTTPS Identity and Tumblebug's selected region.
        return nks.loginTrust(credential, region);
    }

    static void requireRegion(String region) {
        if (region == null || !region.matches("(?i)(?:kr[123]|jp1)"))
            throw new StorageOperationException(503, "NHN_REGION_MISSING",
                    "The selected NHN connection must provide its NKS region (kr1, kr2, kr3 or jp1).");
    }
    private static String value(Secret secret, String key, int max) {
        String encoded = secret.getData().get(key);
        if (encoded == null || encoded.length() > max * 2) throw new IllegalArgumentException();
        String value = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        if (value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException();
        return value;
    }
}
