package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sNodeGroupAutoscaleRequest;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentHistoryDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequestDTO;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import kr.co.mcmp.softwarecatalog.application.model.AutoscaleCheckSample;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.model.ScalingEvent;
import kr.co.mcmp.softwarecatalog.application.repository.AutoscaleCheckSampleRepository;
import kr.co.mcmp.softwarecatalog.application.repository.ScalingEventRepository;
import kr.co.mcmp.softwarecatalog.application.service.K8sAutoscaleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadRebalancingThresholdsTest {
    private final KubernetesMonitoringService monitor = new KubernetesMonitoringService(
            null, null, null, null, null, null, null, null, null, null, null, null);
    private final KubernetesService deploy = new KubernetesService(null, null, null, null, null, null, null, null, null);

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void independentTogglesAndThresholdsRoundTrip(boolean hpa, boolean rebalancing) throws Exception {
        String json = """
            {"hpaEnabled":%s,"workloadRebalancingEnabled":%s,"cpuThreshold":60,"memoryThreshold":75,
             "rebalancingCpuThreshold":90.5,"rebalancingMemoryThreshold":80}
            """.formatted(hpa, rebalancing);
        DeploymentRequest request = new ObjectMapper().readValue(json, DeploymentRequestDTO.class).toDeploymentRequest();
        SoftwareCatalog catalog = new SoftwareCatalog();
        DeploymentConfigDTO helmConfig = DeploymentConfigDTO.from(request, catalog);
        assertThat(helmConfig.isHpaEnabled()).isEqualTo(hpa);
        assertThat(helmConfig.getCpuThreshold()).isEqualTo(60.0);
        assertThat(helmConfig.getMemoryThreshold()).isEqualTo(75.0);

        DeploymentHistory history = DeploymentHistory.builder().catalog(catalog).build();
        ReflectionTestUtils.invokeMethod(deploy, "applyDeploymentRequestConfig", history, request, catalog);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(monitor, "isWorkloadRebalancingEnabled", history)).isEqualTo(rebalancing);
        assertThat(history.getRebalancingCpuThreshold()).isEqualTo(rebalancing ? 90.5 : null);
        assertThat(history.getRebalancingMemoryThreshold()).isEqualTo(rebalancing ? 80.0 : null);
        DeploymentHistoryDTO view = new DeploymentHistoryDTO(history);
        assertThat(view.getCpuThreshold()).isEqualTo(60.0);
        assertThat(view.getRebalancingCpuThreshold()).isEqualTo(rebalancing ? 90.5 : null);
    }

    @Test
    void preservesLegacyThresholdsIncludingTestModeButHonorsExplicitValues() {
        SoftwareCatalog catalog = new SoftwareCatalog();
        catalog.setMemoryThreshold(84.0);
        DeploymentHistory history = DeploymentHistory.builder().catalog(catalog)
                .workloadRebalancingEnabled(true).cpuThreshold(60.0).build();
        assertThat(cpuThreshold(history)).isEqualTo(60.0);
        assertThat(memoryThreshold(history)).isEqualTo(84.0);
        ReflectionTestUtils.setField(monitor, "testMode", true);
        ReflectionTestUtils.setField(monitor, "testCpuThreshold", 0.1);
        ReflectionTestUtils.setField(monitor, "testMemoryThreshold", 0.2);
        assertThat(cpuThreshold(history)).isEqualTo(0.1);
        assertThat(memoryThreshold(history)).isEqualTo(0.2);
        history.setRebalancingCpuThreshold(90.0);
        history.setRebalancingMemoryThreshold(80.0);
        assertThat(cpuThreshold(history)).isEqualTo(90.0);
        assertThat(memoryThreshold(history)).isEqualTo(80.0);
        assertThat(history.getCpuThreshold()).isEqualTo(60.0);
    }

    @ParameterizedTest
    @CsvSource({"70,70,false", "90,80,false", "91,70,true", "70,81,true", "91,81,true"})
    void rebalancingChecksItsOwnCpuAndMemoryIndependently(double cpuUsage, double memoryUsage, boolean expected) {
        DeploymentHistory history = DeploymentHistory.builder().hpaEnabled(true).workloadRebalancingEnabled(true)
                .cpuThreshold(60.0).memoryThreshold(65.0)
                .rebalancingCpuThreshold(90.0).rebalancingMemoryThreshold(80.0).build();
        boolean cpu = ReflectionTestUtils.invokeMethod(monitor, "isThresholdExceeded", cpuUsage, cpuThreshold(history));
        boolean memory = ReflectionTestUtils.invokeMethod(monitor, "isThresholdExceeded", memoryUsage, memoryThreshold(history));
        assertThat(cpu || memory).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 0, 100.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidThresholdsBeforeDeployment(double value) {
        assertThatIllegalArgumentException().isThrownBy(() -> deploy.deployApplication(DeploymentRequest.builder()
                .workloadRebalancingEnabled(true).rebalancingCpuThreshold(value).build())).withMessageContaining("CPU threshold");
        assertThatIllegalArgumentException().isThrownBy(() -> deploy.deployApplication(DeploymentRequest.builder()
                .workloadRebalancingEnabled(true).rebalancingMemoryThreshold(value).build())).withMessageContaining("memory threshold");
    }

    @Test
    void acceptsLegacyMissingFieldsAndPercentageBounds() {
        DeploymentRequest.builder().workloadRebalancingEnabled(true).build().validateRebalancingThresholds();
        DeploymentRequest.builder().workloadRebalancingEnabled(true)
                .rebalancingCpuThreshold(0.01).rebalancingMemoryThreshold(100.0).build().validateRebalancingThresholds();
        DeploymentRequest.builder().workloadRebalancingEnabled(false)
                .rebalancingCpuThreshold(-1.0).build().validateRebalancingThresholds();
    }

    @Test
    void mapsOnlyHpaTargetsToTheNginxChart() {
        HelmChart chart = new HelmChart();
        chart.setChartName("nginx");
        chart.setChartVersion("0.16.8");
        chart.setChartRepositoryUrl("https://cloudpirates-io.github.io/helm-charts");
        DeploymentConfigDTO config = DeploymentConfigDTO.from(DeploymentRequest.builder().hpaEnabled(true)
                .cpuThreshold(60.0).memoryThreshold(75.0).rebalancingCpuThreshold(90.0).rebalancingMemoryThreshold(80.0)
                .minReplicas(1).maxReplicas(3).build(), new SoftwareCatalog());
        Map<String, String> cli = new HashMap<>();
        Map<String, Object> yaml = new HashMap<>();
        HelmChartService.applyHpaTargets(chart, config, cli, yaml);
        assertThat(cli).isEmpty();
        assertThat(yaml).containsEntry("autoscaling", Map.of("minReplicas", "1", "maxReplicas", "3",
                "targetCPUUtilizationPercentage", "60", "targetMemoryUtilizationPercentage", "75"));
        chart.setChartRepositoryUrl("https://charts.bitnami.com/bitnami");
        yaml.clear();
        HelmChartService.applyHpaTargets(chart, config, cli, yaml);
        assertThat(yaml).isEmpty();
        assertThat(cli).containsEntry("autoscaling.targetCPUUtilizationPercentage", "60");
    }

    @Test
    void patchesOnlyTheTargetReleaseAndWaitsForTheHpaReplicaCountToBecomeReady() {
        KubernetesServer server = new KubernetesServer(false, true);
        server.before();
        try {
            var client = server.getClient();
            LocalDateTime triggered = LocalDateTime.now().minusMinutes(1);
            String created = triggered.plusSeconds(15).atZone(ZoneId.systemDefault()).toInstant().toString();
            Node worker = new NodeBuilder().withNewMetadata().withName("worker-2").withCreationTimestamp(created)
                    .addToLabels("zone", "a").endMetadata().withNewSpec().withProviderID("aws:///zone/i-2").endSpec().build();
            client.nodes().resource(worker).create();
            var workloads = client.apps().deployments().inNamespace("default");
            var target = workloads.resource(new DeploymentBuilder().withNewMetadata().withName("nginx-test").withGeneration(1L)
                    .addToLabels("app.kubernetes.io/instance", "nginx-test").endMetadata()
                    .withNewSpec().withReplicas(3).withNewTemplate().withNewSpec().addToNodeSelector("zone", "a")
                    .endSpec().endTemplate().endSpec().build()).create();
            workloads.resource(new DeploymentBuilder(target).editMetadata().withName("nginx-other").withResourceVersion(null)
                    .addToLabels("app.kubernetes.io/instance", "nginx-other").endMetadata().build()).create();
            DeploymentHistory deployment = DeploymentHistory.builder().id(42L).releaseName("nginx-test").hpaEnabled(true).build();
            ScalingEvent event = ScalingEvent.builder().id(7L).triggeredAt(triggered).build();
            var group = new K8sClusterDto.NodeGroup();
            group.setNodes(List.of(new K8sClusterDto.IID("private-name", "i-2")));
            assertThat((Boolean) ReflectionTestUtils.invokeMethod(monitor, "belongsToNodeGroup", worker, group)).isTrue();
            assertThat((Boolean) ReflectionTestUtils.invokeMethod(monitor, "reconcileRebalancingRollout", deployment, event, client, List.of(worker))).isFalse();
            var patched = workloads.withName("nginx-test").get();
            assertThat(patched.getSpec().getReplicas()).isEqualTo(3);
            assertThat(patched.getSpec().getTemplate().getSpec().getNodeSelector()).containsAllEntriesOf(Map.of("zone", "a", "mcmp.io/rebalance-42", "event-7"));
            assertThat(workloads.withName("nginx-other").get().getSpec().getTemplate().getSpec().getNodeSelector()).containsOnlyKeys("zone");
            assertThat(client.nodes().withName("worker-2").get().getMetadata().getLabels()).containsEntry("zone", "a");

            assertThat((Boolean) ReflectionTestUtils.invokeMethod(monitor, "reconcileRebalancingRollout", deployment, event, client, List.of(worker))).isFalse();
            long generation = java.util.Objects.requireNonNullElse(patched.getMetadata().getGeneration(), 1L);
            var ready = new DeploymentBuilder(patched)
                    .editMetadata().withGeneration(generation).endMetadata()
                    .withNewStatus().withObservedGeneration(generation).withReplicas(3).withUpdatedReplicas(3).withReadyReplicas(3)
                    .endStatus().build();
            assertThat(KubernetesMonitoringService.isRebalancingRolloutReady(ready)).isTrue();
            assertThat(KubernetesMonitoringService.isRebalancingRolloutReady(new DeploymentBuilder(ready)
                    .editSpec().withReplicas(4).endSpec().build())).isFalse();
            assertThat(KubernetesMonitoringService.isRebalancingRolloutReady(new DeploymentBuilder(ready)
                    .editMetadata().withGeneration(generation + 1).endMetadata().build())).isFalse();
        } finally {
            server.after();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutPatchRemovesOnlyTheSelectorOwnedByTheEvent() {
        var workload = new DeploymentBuilder().withNewMetadata().withName("nginx-test").endMetadata()
                .withNewSpec().withNewTemplate().withNewSpec().addToNodeSelector("zone", "a")
                .addToNodeSelector("mcmp.io/rebalance-42", "event-7").endSpec().endTemplate().endSpec().build();
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(NonNamespaceOperation.class, RETURNS_DEEP_STUBS);
        var list = new DeploymentList();
        list.setItems(List.of(workload));
        when(client.apps().deployments().inNamespace("default")).thenReturn(deployments);
        when(deployments.withLabel("app.kubernetes.io/instance", "nginx-test").list()).thenReturn(list);
        RollableScalableResource<Deployment> resource = mock(RollableScalableResource.class);
        when(deployments.withName("nginx-test")).thenReturn(resource);
        var history = DeploymentHistory.builder().id(42L).releaseName("nginx-test").build();
        ReflectionTestUtils.invokeMethod(monitor, "clearTimedOutPlacement", history, ScalingEvent.builder().id(6L).build(), client);
        ReflectionTestUtils.invokeMethod(monitor, "clearTimedOutPlacement", history, ScalingEvent.builder().id(7L).build(), client);
        verify(resource).patch(any(PatchContext.class),
                eq("[{\"op\":\"test\",\"path\":\"/spec/template/spec/nodeSelector/mcmp.io~1rebalance-42\",\"value\":\"event-7\"},{\"op\":\"remove\",\"path\":\"/spec/template/spec/nodeSelector/mcmp.io~1rebalance-42\"}]"));
    }

    @ParameterizedTest
    @CsvSource({"75,true,2,false", "91,false,2,false", "91,true,1,false", "91,true,2,true"})
    void requestsWorkerOnlyForSustainedRebalancingPressureWithinTheWorkerCap(
            double cpu, boolean sustained, int workerCap, boolean expected) {
        var samples = mock(AutoscaleCheckSampleRepository.class);
        var events = mock(ScalingEventRepository.class);
        var autoscale = mock(K8sAutoscaleService.class);
        var tumblebug = mock(CbtumblebugRestApi.class);
        var service = new KubernetesMonitoringService(null, null, null, events, null, samples, null, null, autoscale, tumblebug, null, null);
        ReflectionTestUtils.setField(service, "defaultMaxNodes", workerCap);
        ReflectionTestUtils.setField(service, "autoscaleSampleIntervalSeconds", 10);
        ReflectionTestUtils.setField(service, "autoscaleEvaluationWindowSeconds", 20);
        ReflectionTestUtils.setField(service, "autoscaleMinSampleCoverageRatio", 1.0);
        ReflectionTestUtils.setField(service, "autoscaleRequiredExceedanceRatio", 1.0);
        ReflectionTestUtils.setField(service, "autoscaleRequiredConsecutiveExceedanceSeconds", 20);
        when(samples.findByDeploymentIdAndCheckedAtBetweenOrderByCheckedAtAsc(eq(42L), any(), any()))
                .thenReturn(List.of(AutoscaleCheckSample.builder().thresholdExceeded(sustained).build(),
                        AutoscaleCheckSample.builder().thresholdExceeded(true).build()));
        var group = new K8sClusterDto.NodeGroup();
        group.setName("worker-group");
        group.setNodes(List.of(new K8sClusterDto.IID("worker-1", "i-1")));
        group.setDesiredNodeSize(1);
        group.setMinNodeSize(1);
        group.setMaxNodeSize(4);
        group.setOnAutoScaling(true);
        var cluster = new K8sClusterDto();
        cluster.setNodeGroups(List.of(group));
        when(tumblebug.getK8sClusterByName("project", "test")).thenReturn(cluster);
        var history = DeploymentHistory.builder().id(42L).namespace("project").clusterName("test").nodeGroupName("worker-group")
                .releaseName("nginx-test").deploymentType(DeploymentType.K8S).hpaEnabled(true).maxReplicas(1)
                .workloadRebalancingEnabled(true).cpuThreshold(60.0).rebalancingCpuThreshold(90.0).rebalancingMemoryThreshold(80.0).build();
        KubernetesServer server = new KubernetesServer(false, true);
        server.before();
        try {
            var client = server.getClient();
            client.apps().deployments().inNamespace("default").resource(new DeploymentBuilder().withNewMetadata()
                    .withName("nginx-test").addToLabels("app.kubernetes.io/instance", "nginx-test").endMetadata().build()).create();
            ReflectionTestUtils.invokeMethod(service, "handleK8sAutoscaling", history,
                    ApplicationStatus.builder().cpuUsage(cpu).memoryUsage(70.0).build(), client);
            if (expected) {
                verify(autoscale).changeNodeGroupAutoscaleSize("project", "test", "worker-group",
                        new K8sNodeGroupAutoscaleRequest("2", "4", "1"));
            } else {
                verifyNoInteractions(autoscale);
            }
        } finally {
            server.after();
        }
    }

    private Double cpuThreshold(DeploymentHistory history) {
        return ReflectionTestUtils.invokeMethod(monitor, "getAutoscaleCpuThreshold", history);
    }

    private Double memoryThreshold(DeploymentHistory history) {
        return ReflectionTestUtils.invokeMethod(monitor, "getAutoscaleMemoryThreshold", history);
    }
}
