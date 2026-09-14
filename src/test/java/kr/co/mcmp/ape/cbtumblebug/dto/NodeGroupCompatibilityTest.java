package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class NodeGroupCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @ParameterizedTest
    @ValueSource(strings = {"nodeGroupId", "subGroupId"})
    void normalizesGroupForFrontendAndDeploymentResolver(String field) throws Exception {
        VmAccessInfo vm = mapper.readValue("{\"id\":\"ng-aws-1\",\"" + field
                + "\":\"ng-aws\",\"status\":\"Running\"}", VmAccessInfo.class);
        assertThat(vm.getSubGroupId()).isEqualTo("ng-aws");
        assertThat(mapper.readTree(mapper.writeValueAsString(vm)).get("subGroupId").asText())
                .isEqualTo("ng-aws");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readsAccessInfoInBothTumblebugFormats(boolean modern) throws Exception {
        String json = modern
                ? "{\"InfraId\":\"infra\",\"InfraNodeGroupAccessInfo\":[{\"NodeGroupId\":\"ng\",\"BastionNodeId\":\"b\",\"NodeAccessInfo\":[{\"nodeId\":\"vm\",\"privateKey\":\"test-key\"}]}]}"
                : "{\"MciId\":\"infra\",\"MciSubGroupAccessInfo\":[{\"SubGroupId\":\"ng\",\"BastionVmId\":\"b\",\"MciVmAccessInfo\":[{\"vmId\":\"vm\",\"privateKey\":\"test-key\"}]}]}";
        MciAccessInfoDto result = mapper.readValue(json, MciAccessInfoDto.class);
        assertThat(result.getMciId()).isEqualTo("infra");
        var group = result.getMciSubGroupAccessInfo().get(0);
        assertThat(group.getSubGroupId()).isEqualTo("ng");
        assertThat(group.getBastionVmId()).isEqualTo("b");
        assertThat(group.getMciVmAccessInfo().get(0).getVmId()).isEqualTo("vm");
        assertThat(group.getMciVmAccessInfo().get(0).getPrivateKey()).isEqualTo("test-key");
    }
}
