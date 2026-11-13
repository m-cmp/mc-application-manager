package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import kr.co.mcmp.softwarecatalog.common.service.RabbitMqAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadRebalancingThresholdsTest {
    private final KubernetesMonitoringService monitor = new KubernetesMonitoringService(
            null, null, null, null, null, null, null, null, null, null, null, null, null);
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
    void alertsOnceOnlyAfterAllHpaReplicasAreReadyOnTheNewWorker() {
        KubernetesServer server = new KubernetesServer(false, true);
        server.before();
        try {
            var client = server.getClient();
            var events = mock(ScalingEventRepository.class);
            var tumblebug = mock(CbtumblebugRestApi.class);
            var rabbit = mock(RabbitMqAlertService.class);
            var service = new KubernetesMonitoringService(null, null, null, events, null, null, null,
                    null, null, tumblebug, rabbit, null, null);
            ReflectionTestUtils.setField(service, "scalingTimeoutSeconds", 300);
            ReflectionTestUtils.setField(service, "defaultSlackChannelId", "C0123456789");
            LocalDateTime triggered = LocalDateTime.now().minusMinutes(1);
            String created = triggered.plusSeconds(15).atZone(ZoneId.systemDefault()).toInstant().toString();
            Node worker = new NodeBuilder().withNewMetadata().withName("worker-2").withCreationTimestamp(created)
                    .addToLabels("zone", "a").endMetadata().withNewSpec().withProviderID("aws:///zone/i-2").endSpec()
                    .withNewStatus().addNewCondition().withType("Ready").withStatus("True").endCondition().endStatus().build();
            client.nodes().resource(worker).create();
            var workloads = client.apps().deployments().inNamespace("default");
            var target = workloads.resource(new DeploymentBuilder().withNewMetadata().withName("nginx-test").withGeneration(1L)
                    .addToLabels("app.kubernetes.io/instance", "nginx-test").endMetadata()
                    .withNewSpec().withReplicas(3).withNewTemplate().withNewSpec().addToNodeSelector("zone", "a")
                    .endSpec().endTemplate().endSpec().build()).create();
            workloads.resource(new DeploymentBuilder(target).editMetadata().withName("nginx-other").withResourceVersion(null)
                    .addToLabels("app.kubernetes.io/instance", "nginx-other").endMetadata().build()).create();
            var catalog = new SoftwareCatalog();
            catalog.setName("Nginx");
            DeploymentHistory deployment = DeploymentHistory.builder().id(42L).releaseName("nginx-test").hpaEnabled(true)
                    .deploymentType(DeploymentType.K8S).namespace("project").clusterName("test").catalog(catalog).build();
            ScalingEvent event = ScalingEvent.builder().id(7L).deploymentId(42L).triggeredAt(triggered)
                    .namespace("project").clusterName("test").nodeGroupName("workers").newNodeCount(2).build();
            var group = new K8sClusterDto.NodeGroup();
            group.setName("workers");
            group.setNodes(List.of(new K8sClusterDto.IID("worker-1", "i-1"), new K8sClusterDto.IID("private-name", "i-2")));
            var cluster = new K8sClusterDto();
            cluster.setNodeGroups(List.of(group));
            when(tumblebug.getK8sClusterByName("project", "test")).thenReturn(cluster);
            when(events.findPendingScalingEvents(42L)).thenReturn(List.of(event));
            when(events.finishPendingEvent(eq(7L), eq(ScalingEvent.ScalingStatus.COMPLETED), any(), eq(null)))
                    .thenReturn(1, 0);
            String label = "mcmp.io/scale-out-42";

            checkPendingScaleOut(service, deployment, client);
            var patched = workloads.withName("nginx-test").get();
            assertThat(patched.getSpec().getReplicas()).isEqualTo(3);
            assertThat(patched.getSpec().getTemplate().getSpec().getNodeSelector()).containsAllEntriesOf(Map.of("zone", "a", label, "7"));
            assertThat(workloads.withName("nginx-other").get().getSpec().getTemplate().getSpec().getNodeSelector()).containsOnlyKeys("zone");
            assertThat(client.nodes().withName("worker-2").get().getMetadata().getLabels()).containsEntry("zone", "a").containsEntry(label, "7");
            verifyNoInteractions(rabbit);

            long generation = java.util.Objects.requireNonNullElse(patched.getMetadata().getGeneration(), 1L);
            workloads.resource(new DeploymentBuilder(patched)
                    .editMetadata().withGeneration(generation).endMetadata()
                    .withNewStatus().withObservedGeneration(generation).withReplicas(3).withUpdatedReplicas(3).withReadyReplicas(3)
                    .endStatus().build()).update();
            var pods = client.pods().inNamespace("default");
            for (int i = 0; i < 3; i++) {
                pods.resource(new PodBuilder().withNewMetadata().withName("nginx-test-" + i).addToLabels(label, "7")
                        .endMetadata().withNewSpec().withNodeName(i == 0 ? "worker-1" : "worker-2").endSpec()
                        .withNewStatus().withPhase("Running").addNewCondition().withType("Ready").withStatus("True")
                        .endCondition().endStatus().build()).create();
            }
            checkPendingScaleOut(service, deployment, client);
            verifyNoInteractions(rabbit);

            pods.withName("nginx-test-0").delete();
            pods.resource(new PodBuilder(pods.withName("nginx-test-1").get()).editMetadata()
                    .withName("nginx-test-replacement").withResourceVersion(null).endMetadata().build()).create();
            checkPendingScaleOut(service, deployment, client);
            checkPendingScaleOut(service, deployment, client);
            verify(rabbit, times(1)).sendScaleOutAlert(eq("[K8s AutoScaling] Scale Out Completed: Nginx"),
                    any(), eq("slack"), eq(List.of("C0123456789")));
        } finally {
            server.after();
        }
    }

    @Test
    void timeoutPatchRemovesOnlyTheSelectorOwnedByTheEvent() {
        var server = new KubernetesServer(false, true);
        server.before();
        try {
            var client = server.getClient();
            var workloads = client.apps().deployments().inNamespace("default");
            String label = "mcmp.io/scale-out-42";
            var workload = workloads.resource(new DeploymentBuilder().withNewMetadata().withName("nginx-test").endMetadata()
                    .withNewSpec().withNewTemplate().withNewMetadata().addToLabels(label, "7").endMetadata()
                    .withNewSpec().addToNodeSelector("zone", "a").addToNodeSelector(label, "7")
                    .endSpec().endTemplate().endSpec().build()).create();
            KubernetesScaleOutRollout.releasePlacement(client, "default", workload, ScalingEvent.builder().deploymentId(42L).id(6L).build());
            assertThat(workloads.withName("nginx-test").get().getSpec().getTemplate().getSpec().getNodeSelector())
                    .containsEntry(label, "7");
            KubernetesScaleOutRollout.releasePlacement(client, "default", workload, ScalingEvent.builder().deploymentId(42L).id(7L).build());
            var template = workloads.withName("nginx-test").get().getSpec().getTemplate();
            assertThat(template.getSpec().getNodeSelector()).containsOnlyKeys("zone");
            assertThat(template.getMetadata().getLabels()).doesNotContainKey(label);
        } finally {
            server.after();
        }
    }

    private static void checkPendingScaleOut(KubernetesMonitoringService service, DeploymentHistory history,
            KubernetesClient client) {
        ReflectionTestUtils.invokeMethod(service, "handleK8sAutoscaling", history, ApplicationStatus.builder().build(),
                client, "default");
    }

    @ParameterizedTest
    @CsvSource({"75,true,2,false", "91,false,2,false", "91,true,1,false", "91,true,2,true"})
    void requestsWorkerOnlyForSustainedRebalancingPressureWithinTheWorkerCap(
            double cpu, boolean sustained, int workerCap, boolean expected) {
        var samples = mock(AutoscaleCheckSampleRepository.class);
        var events = mock(ScalingEventRepository.class);
        var autoscale = mock(K8sAutoscaleService.class);
        var tumblebug = mock(CbtumblebugRestApi.class);
        var service = new KubernetesMonitoringService(null, null, null, events, null, samples, null, null, autoscale, tumblebug, null, null, null);
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
                .workloadRebalancingEnabled(true).cpuThreshold(60.0).rebalancingCpuThreshold(90.0).rebalancingMemoryThreshold(80.0)
                .catalog(new SoftwareCatalog()).build();
        KubernetesServer server = new KubernetesServer(false, true);
        server.before();
        try {
            var client = server.getClient();
            client.apps().deployments().inNamespace("default").resource(new DeploymentBuilder().withNewMetadata()
                    .withName("nginx-test").addToLabels("app.kubernetes.io/instance", "nginx-test").endMetadata().build()).create();
            ReflectionTestUtils.invokeMethod(service, "handleK8sAutoscaling", history,
                    ApplicationStatus.builder().cpuUsage(cpu).memoryUsage(70.0).build(), client, "default");
            if (expected) {
                verify(autoscale).changeNodeGroupAutoscaleSize("project", "test", "worker-group",
                        new K8sNodeGroupAutoscaleRequest(2, 4, 1));
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
