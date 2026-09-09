package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckResult;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesNamespaces;
import kr.co.mcmp.softwarecatalog.service.SoftwareSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesIngressPreflightService {
    private final CatalogRepository catalogs;
    private final SoftwareSourceService sources;
    private final KubernetesClientFactory clients;

    /** No installation, firewall changes, certificate generation or deployment-history writes. */
    @Transactional(readOnly = true)
    public K8sIngressCheckResult check(K8sIngressCheckRequest request) {
        var catalog = catalogs.findById(request.getCatalogId())
                .orElseThrow(() -> new IllegalArgumentException("Software catalog not found"));
        var config = DeploymentConfigDTO.from(request.toDeploymentRequest(), catalog);
        if (!config.isIngressEnabled()) return new K8sIngressCheckResult(true, List.of(), List.of());

        boolean nativeJupyter = catalog.getPackageInfo() != null && catalog.getPackageInfo().getPackageName() != null
                && catalog.getPackageInfo().getPackageName().toLowerCase(Locale.ROOT).contains("jupyter");
        try {
            var chart = nativeJupyter ? HelmChart.builder().chartName("jupyter-native").build()
                    : sources.getArtifactHubSource(catalog.getId())
                            .orElseThrow(() -> new IllegalArgumentException("No ArtifactHub source found for this catalog"));
            HelmIngressValues.validate(chart, config);
            // Preserve the native Jupyter service's existing dedicated-host / HTTP-only policy.
            if (nativeJupyter && (!"/".equals(config.getIngressPath()) || config.getIngressHost().startsWith("*."))) {
                throw new IllegalArgumentException("Jupyter requires a dedicated DNS hostname with path /.");
            }
            if (nativeJupyter && config.isTlsEnabled()) {
                throw new IllegalArgumentException("K8s Jupyter uses HTTP NodePort 30880; configure a separate HTTPS entry before enabling TLS.");
            }
        } catch (IllegalArgumentException e) {
            return invalid(e.getMessage());
        }

        try (var client = clients.getClient(request.getNamespace(), request.getClusterName())) {
            KubernetesIngressRouteValidator.assertAvailable(client, config, nativeJupyter);
            return new K8sIngressCheckResult(true, List.of(), KubernetesIngressTlsWarnings.inspect(
                    client, KubernetesNamespaces.APPLICATION_WORKLOAD, config));
        } catch (KubernetesIngressRouteValidator.ConflictException | KubernetesIngressRouteValidator.LookupException e) {
            return invalid(e.getMessage());
        } catch (RuntimeException e) {
            // Never turn a failed cluster lookup into a successful preflight or expose kubeconfig details.
            log.warn("Ingress preflight could not reach the selected cluster ({})", e.getClass().getSimpleName());
            return invalid("Cannot check Ingress routes on the selected cluster. Check connectivity and access permissions, then retry Spec Check.");
        }
    }

    private static K8sIngressCheckResult invalid(String message) {
        return new K8sIngressCheckResult(false, List.of(message), List.of());
    }
}
