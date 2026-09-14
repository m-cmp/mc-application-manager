package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckRequest;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressCheckResult;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressTlsSettings;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
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
    private final K8sIngressAccessService ingressAccess;
    private final IbmIngressAutomationService automation;

    @Transactional(readOnly = true)
    public K8sIngressTlsSettings tlsSettings(String namespace, String clusterName) {
        var target = kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest.builder()
                .namespace(namespace).clusterName(clusterName).ingressEnabled(true).ingressClass("nginx").build();
        ingressAccess.resolveTarget(target, new kr.co.mcmp.softwarecatalog.SoftwareCatalog());
        if (!IbmIngressSupport.managed(target.getIngressClass())) return new K8sIngressTlsSettings(false, null, List.of(), List.of());
        try (var client = clients.getClient(namespace, clusterName)) {
            return IbmIngressTlsResolver.describe(client, KubernetesNamespaces.APPLICATION_WORKLOAD, target.getIngressClass());
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Cannot discover IBM HTTPS settings. Check cluster connectivity and read permissions."); }
    }

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
        } catch (IllegalArgumentException e) {
            return invalid(e.getMessage());
        }

        try (var client = clients.getClient(request.getNamespace(), request.getClusterName())) {
            var target = request.toDeploymentRequest();
            ingressAccess.resolveTarget(target, catalog);
            config = DeploymentConfigDTO.from(target, catalog);
            if (nativeJupyter && config.isTlsEnabled() && !IbmIngressSupport.managed(config.getIngressClass())) {
                return invalid("K8s Jupyter uses HTTP NodePort 30880; configure a separate HTTPS entry before enabling TLS.");
            }
            String workloadNamespace = nativeJupyter ? request.getNamespace() : KubernetesNamespaces.APPLICATION_WORKLOAD;
            KubernetesIngressRouteValidator.assertAvailable(client, config, nativeJupyter);
            if (IbmIngressSupport.managed(config.getIngressClass())) K8sIngressPolicy.validate(target, config);
            var warnings = new java.util.ArrayList<String>();
            if (IbmIngressSupport.managed(config.getIngressClass())) {
                warnings.addAll(automation.check(client, request.getNamespace(), request.getClusterName(), workloadNamespace, config));
            }
            if (!IbmIngressSupport.managed(config.getIngressClass())) {
                warnings.addAll(KubernetesIngressTlsWarnings.inspect(client, workloadNamespace, config));
            }
            return new K8sIngressCheckResult(true, List.of(), warnings);
        } catch (KubernetesIngressRouteValidator.ConflictException | KubernetesIngressRouteValidator.LookupException e) {
            return invalid(e.getMessage());
        } catch (IllegalArgumentException | ApplicationException e) {
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
