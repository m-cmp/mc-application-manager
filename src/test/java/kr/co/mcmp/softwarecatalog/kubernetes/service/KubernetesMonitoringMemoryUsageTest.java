package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;

class KubernetesMonitoringMemoryUsageTest {

    private final KubernetesMonitoringService service = new KubernetesMonitoringService(
            null, null, null, null, null, null, null, null, null, null, null, null);

    @ParameterizedTest
    @CsvSource({
            "33554432Ki,512Mi", "32Gi,512Mi", "32768Mi,512Mi",
            "34359738368,512Mi", "34.359738368G,512Mi", "0.03125Ti,512Mi",
            "32Gi,524288Ki", "32Gi,0.5Gi", "32Gi,536870912", "32Gi,536.870912M"
    })
    void equivalentUnitsGiveTheSameMemoryPercentage(String capacity, String usage) {
        Map<String, Object> result = usage(List.of(capacity), List.of(usage));
        assertThat(result.get("memoryPercentage")).isEqualTo(1.56);
        assertThat(result.get("memoryBytes")).isEqualTo(536870912L);
        assertThat(result.get("cpuPercentage")).isEqualTo(3.13);
    }

    @Test
    void aggregatesMultipleNodesAndPodsWithDifferentUnits() {
        Map<String, Object> result = usage(List.of("16Gi", "16777216Ki"), List.of("256Mi", "262144Ki"));
        assertThat(result.get("memoryPercentage")).isEqualTo(1.56);
        assertThat(result.get("memoryBytes")).isEqualTo(536870912L);
        assertThat(result.get("cpuPercentage")).isEqualTo(3.13);
    }

    @ParameterizedTest
    @CsvSource({"32Gi,0,0.0", "32Gi,32Gi,100.0", "32Gi,1Gi,3.13", "8Gi,2Gi,25.0"})
    void preservesZeroFullAndFractionalUsage(String capacity, String memory, double expected) {
        assertThat(usage(List.of(capacity), List.of(memory)).get("memoryPercentage")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"524288Ki,0.5", "512Mi,0.5", "0.5Gi,0.5", "536870912,0.5", "536.870912M,0.5", "0.00048828125Ti,0.5"})
    void convertsMemoryToGiBWithoutDroppingItsUnit(String value, double expected) {
        Double actual = ReflectionTestUtils.invokeMethod(service, "parseMemoryUsage", value);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void malformedMemoryRemainsNonFatal() {
        assertThat((Double) ReflectionTestUtils.invokeMethod(service, "parseMemoryUsage", "not-memory")).isZero();
        assertThat((Double) ReflectionTestUtils.invokeMethod(service, "parseMemoryUsage", "")).isZero();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> usage(List<String> capacities, List<String> memories) {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        NodeList nodes = new NodeList();
        nodes.setItems(capacities.stream().map(memory -> new NodeBuilder()
                .withNewMetadata().withName("worker").endMetadata()
                .withNewStatus().withCapacity(Map.of("memory", new Quantity(memory), "cpu", new Quantity("8")))
                .endStatus().build()).toList());
        when(client.nodes().list()).thenReturn(nodes);

        List<Pod> pods = java.util.stream.IntStream.range(0, memories.size()).mapToObj(i -> new PodBuilder()
                .withNewMetadata().withName("nginx-" + i).endMetadata()
                .withNewSpec().endSpec().withNewStatus().withPhase("Running").endStatus().build()).toList();
        GenericKubernetesResourceList metrics = new GenericKubernetesResourceList();
        metrics.setItems(java.util.stream.IntStream.range(0, memories.size()).mapToObj(i -> {
            GenericKubernetesResource metric = new GenericKubernetesResource();
            metric.setMetadata(new ObjectMetaBuilder().withName("nginx-" + i).build());
            metric.setAdditionalProperty("containers", List.of(Map.of("usage", Map.of("cpu", "250m", "memory", memories.get(i)))));
            return metric;
        }).toList());
        MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> metricsOperation = mock(MixedOperation.class);
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class))).thenReturn(metricsOperation);
        when(metricsOperation.inNamespace("default")).thenReturn(metricsOperation);
        when(metricsOperation.list()).thenReturn(metrics);
        ServiceList services = new ServiceList();
        services.setItems(List.of());
        MixedOperation<Service, ServiceList, ServiceResource<Service>> servicesOperation = mock(MixedOperation.class);
        when(client.services()).thenReturn(servicesOperation);
        when(servicesOperation.inNamespace("default")).thenReturn(servicesOperation);
        when(servicesOperation.withLabel(anyString(), anyString())).thenReturn(servicesOperation);
        when(servicesOperation.list()).thenReturn(services);
        return ReflectionTestUtils.invokeMethod(service, "getResourceUsagePercentage",
                client, "default", "nginx", "nginx", pods, (long) pods.size(), 0L, 0L);
    }
}
