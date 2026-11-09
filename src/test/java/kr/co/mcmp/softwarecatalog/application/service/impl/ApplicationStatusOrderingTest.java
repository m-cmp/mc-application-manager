package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;

class ApplicationStatusOrderingTest {
    @Test void sortsByProjectInfraGroupAndNodeUsingNaturalNumericOrder() {
        var rows = new ArrayList<>(List.of(
                row("p2", "m1", "g1", "v1"), row("p1", "m10", "g1", "v1"),
                row("p1", "m2", "g10", "v1"), row("p1", "m2", "g2", "v10"),
                row("p1", "m2", "g2", "v2"), row("p1", "m1", null, "v1")));
        rows.sort(ApplicationStatusOrdering.ORDER);
        assertThat(rows).extracting(r -> String.join("/", r.getNamespace(), r.getMciId(),
                String.valueOf(r.getNodeGroupName()), r.getVmId())).containsExactly(
                        "p1/m1/null/v1", "p1/m2/g2/v2", "p1/m2/g2/v10", "p1/m2/g10/v1", "p1/m10/g1/v1", "p2/m1/g1/v1");
    }
    @Test void handlesLegacyNullsK8sClustersLargeNumbersAndLeadingZeros() {
        var k8s = row("p1", null, null, null); k8s.setDeploymentType(DeploymentType.K8S); k8s.setClusterName("k1");
        var rows = new ArrayList<>(List.of(row(null, null, null, null), row("p1", "m99999999999999999999", null, null),
                row("p1", "m02", null, null), row("p1", "m2", null, null), k8s));
        assertThatCode(() -> rows.sort(ApplicationStatusOrdering.ORDER)).doesNotThrowAnyException();
        assertThat(rows.get(0)).isSameAs(k8s);
        assertThat(rows.get(4).getNamespace()).isNull();
        for (var a : rows) for (var b : rows) {
            assertThat(Integer.signum(ApplicationStatusOrdering.ORDER.compare(a, b)))
                    .isEqualTo(-Integer.signum(ApplicationStatusOrdering.ORDER.compare(b, a)));
        }
    }
    private ApplicationStatusDto row(String project, String infra, String group, String vm) {
        return ApplicationStatusDto.builder().namespace(project).mciId(infra).nodeGroupName(group).vmId(vm)
                .deploymentType(DeploymentType.VM).build();
    }
}
