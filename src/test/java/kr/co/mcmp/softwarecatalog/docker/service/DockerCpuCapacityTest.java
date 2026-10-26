package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class DockerCpuCapacityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"1,80,80", "2,80,40", "4,80,20", "2,175,87.5", "4,400,100", "2,0,0"})
    void normalizesByActualHostCpuCount(String cpus, double raw, double expected) throws Exception {
        Double capacity = DockerCpuCapacity.resolve(mapper.readTree("{}"), cpus, null, List.of());
        assertThat(DockerCpuCapacity.normalize(raw, capacity)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "'{\"NanoCpus\":500000000}',0.5",
        "'{\"CpuQuota\":50000,\"CpuPeriod\":100000}',0.5",
        "'{\"CpuQuota\":50000,\"CpuPeriod\":0}',0.5",
        "'{\"NanoCpus\":2000000000,\"CpuQuota\":50000,\"CpuPeriod\":100000}',0.5",
        "'{\"CpusetCpus\":\"1,3\"}',2",
        "'{\"CpusetCpus\":\"0-2,1-3,3\"}',4",
        "'{\"NanoCpus\":0,\"CpuQuota\":-1,\"CpuPeriod\":0,\"CpusetCpus\":\"\"}',4"
    })
    void usesTightestExplicitCpuRestriction(String config, double expected) throws Exception {
        assertThat(DockerCpuCapacity.resolve(mapper.readTree(config), "4", null, List.of()))
                .isEqualTo(expected);
    }

    @Test
    void considersInheritedRuntimeQuotaAndActualAllowedCpuSet() throws Exception {
        assertThat(DockerCpuCapacity.resolve(mapper.readTree("{}"), "8", "0-3",
                List.of("max 100000", "150000 100000", "200000 100000")))
                .isEqualTo(1.5);
        assertThat(DockerCpuCapacity.resolve(mapper.readTree("{}"), "8", "0,2",
                List.of("-1 100000"))).isEqualTo(2.0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "2.0", "NaN", "unknown", "9223372036854775808"})
    void neverAssumesOneCpuWhenHostCapacityIsMissingOrInvalid(String cpus) throws Exception {
        assertThat(DockerCpuCapacity.resolve(mapper.readTree("{\"NanoCpus\":1000000000}"),
                cpus, null, List.of())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "[]", "{\"NanoCpus\":-1}", "{\"NanoCpus\":1.5}",
        "{\"CpuQuota\":-2}", "{\"CpuQuota\":10000,\"CpuPeriod\":-1}",
        "{\"CpusetCpus\":\"3-1\"}", "{\"CpusetCpus\":\"0,,2\"}"
    })
    void rejectsMissingHostConfigOrMalformedExplicitRestrictions(String config) throws Exception {
        // An empty HostConfig is valid; a missing HostConfig (as on the root object) is not.
        var parsed = mapper.readTree(config);
        if ("{}".equals(config)) {
            parsed = parsed.path("HostConfig");
        }
        assertThat(DockerCpuCapacity.resolve(parsed, "4", null, List.of())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "0 100000", "100000 0", "-2 100000", "max", "max nope"})
    void rejectsInvalidRuntimeQuotaRatherThanGuessing(String quota) throws Exception {
        assertThat(DockerCpuCapacity.resolve(mapper.readTree("{}"), "4", null, List.of(quota)))
                .isNull();
    }

    @Test
    void doesNotClampInvalidOrOverCapacityValuesToPlausibleMetrics() {
        for (Double invalid : new Double[] {null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 202.01}) {
            assertThat(DockerCpuCapacity.normalize(invalid, 2.0)).isNull();
        }
        assertThat(DockerCpuCapacity.normalize(80.0, null)).isNull();
        assertThat(DockerCpuCapacity.normalize(80.0, 0.0)).isNull();
        assertThat(DockerCpuCapacity.normalize(80.0, Double.NaN)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"200,100", "200.01,100", "201,100", "202,100"})
    void toleratesAtMostOnePercentagePointOfSamplingOvershoot(double raw, double expected) {
        assertThat(DockerCpuCapacity.normalize(raw, 2.0)).isEqualTo(expected);
    }

    @Test
    void countsOverlappingCpuRangesWithoutDoubleCounting() {
        assertThat(DockerCpuCapacity.countCpuList("0-2,2-4,4,7,7")).isEqualTo(6);
        assertThat(DockerCpuCapacity.countCpuList("2147483647")).isEqualTo(1);
        assertThatThrownBy(() -> DockerCpuCapacity.countCpuList("0,2147483648"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
