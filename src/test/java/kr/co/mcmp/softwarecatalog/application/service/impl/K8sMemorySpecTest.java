package kr.co.mcmp.softwarecatalog.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sSpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository;

class K8sMemorySpecTest {
    private final SpecValidationServiceImpl service = new SpecValidationServiceImpl(null, null, null);
    private double memory(String json) throws Exception {
        var spec = new ObjectMapper().readValue(json, K8sSpec.class);
        String raw = ReflectionTestUtils.invokeMethod(service, "getMemoryValueFromSpec", spec);
        return ReflectionTestUtils.invokeMethod(service, "convertMemoryToGB", raw);
    }
    @Test void normalizedSpiderCapacityWinsOverAlibabaZeroAndLegacyValues() throws Exception {
        assertThat(memory("""
                {"MemSizeMib":"16384","KeyValueList":[{"key":"MemorySize","value":"16.00"},{"key":"Memory","value":"0"}]}
                """)).isEqualTo(16);
        assertThat(memory("{\"MemSizeMib\":8192,\"Mem\":\"4GB\"}")).isEqualTo(8);
        assertThat(memory("{\"MemSizeMib\":\"512\"}")).isEqualTo(0.5);
        assertThat(memory("{\"MemSizeMib\":\" 4096 \"}")).isEqualTo(4);
    }
    @Test void olderResponsesAndInvalidNormalizedFieldsStillUseLegacyData() throws Exception {
        for (String invalid : new String[]{"", "0", "-1", "invalid", "NaN", "Infinity"})
            assertThat(memory("{\"MemSizeMib\":\"" + invalid + "\",\"Mem\":\"8GiB\"}")).isEqualTo(8);
        assertThat(memory("{\"Mem\":\"16GB\"}")).isEqualTo(16);
        assertThat(memory("{\"KeyValueList\":[{\"key\":\"MemorySizeMib\",\"value\":\"8192\"}]}")).isEqualTo(8);
        assertThat(memory("{\"KeyValueList\":[{\"key\":\"Memory\",\"value\":\"4\"}]}")).isEqualTo(4);
    }
    @Test void realSpecDecisionUsesCapacityAndStillRejectsOversizedApplications() throws Exception {
        var catalogs = mock(CatalogService.class);
        var histories = mock(DeploymentHistoryRepository.class);
        var validation = spy(new SpecValidationServiceImpl(catalogs, mock(CbtumblebugRestApi.class), histories));
        var spec = new ObjectMapper().readValue("{\"MemSizeMib\":\"16384\",\"VCpu\":{\"Count\":\"4\"},\"KeyValueList\":[{\"key\":\"Memory\",\"value\":\"0\"}]}", K8sSpec.class);
        doReturn(spec).when(validation).getSpecForK8s("test", "cluster");
        when(histories.findByNamespaceAndClusterNameAndActionTypeNotAndStatus(any(), any(), any(), any())).thenReturn(List.of());
        for (double requirement : new double[]{0.5, 1, 8, 16, 16.1, 32}) {
            when(catalogs.getCatalog(1L)).thenReturn(SoftwareCatalogDTO.builder().recommendedCpu(1d).recommendedMemory(requirement).build());
            assertThat(validation.checkSpecForK8s("test", "cluster", 1L)).isEqualTo(requirement <= 16);
        }
    }
}
