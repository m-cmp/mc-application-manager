package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;

@ExtendWith(MockitoExtension.class)
class VmNodeGroupTargetResolverTest {

    @Mock
    private CbtumblebugRestApi cbtumblebugRestApi;

    private VmNodeGroupTargetResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VmNodeGroupTargetResolver(cbtumblebugRestApi);
    }

    @Test
    void resolvesOnlyRunningMembersWhenVmIdsAreOmitted() {
        MciDto mci = mci(
                vm("vm-a", null, "group-a", "Running"),
                vm("vm-b", null, "group-a", "RUNNING"),
                vm("vm-c", null, "group-a", "Stopped"),
                vm("vm-other", null, "group-b", "Running"),
                vm("vm-a", null, "group-a", "Running"),
                vm(null, "vm-by-name", "group-a", "running"));
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a")).thenReturn(mci);

        DeploymentRequest request = nodeGroupRequest(" group-a ", VmDeploymentMode.STANDALONE);

        resolver.resolve(request);

        assertThat(request.getVmNodeGroupId()).isEqualTo("group-a");
        assertThat(request.getVmDeploymentMode()).isEqualTo(VmDeploymentMode.STANDALONE);
        assertThat(request.getVmIds()).containsExactly("vm-a", "vm-b", "vm-by-name");
        verify(cbtumblebugRestApi).getMciByMciId("project-a", "mci-a");
    }

    @Test
    void verifiesSelectedVmMembershipWithoutExpandingToTheWholeGroup() {
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a"))
                .thenReturn(mci(
                        vm("vm-a", null, "group-a", "Running"),
                        vm("vm-b", null, "group-a", "Running")));

        DeploymentRequest request = nodeGroupRequest("group-a", VmDeploymentMode.STANDALONE);
        request.setVmIds(List.of("vm-b"));

        resolver.resolve(request);

        assertThat(request.getVmIds()).containsExactly("vm-b");
        assertThat(request.getVmNodeGroupId()).isEqualTo("group-a");
    }

    @Test
    void rejectsSelectedVmFromAnotherNodeGroup() {
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a"))
                .thenReturn(mci(
                        vm("vm-a", null, "group-a", "Running"),
                        vm("vm-b", null, "group-b", "Running")));

        DeploymentRequest request = nodeGroupRequest("group-a", VmDeploymentMode.STANDALONE);
        request.setVmIds(List.of("vm-b"));

        assertThatThrownBy(() -> resolver.resolve(request))
                .hasMessageContaining("does not belong");
    }

    @Test
    void rejectsSelectedVmThatIsNotRunning() {
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a"))
                .thenReturn(mci(vm("vm-a", null, "group-a", "Stopped")));

        DeploymentRequest request = nodeGroupRequest("group-a", VmDeploymentMode.STANDALONE);
        request.setVmIds(List.of("vm-a"));

        assertThatThrownBy(() -> resolver.resolve(request))
                .hasMessageContaining("not running");
    }

    @Test
    void rejectsClusteringWithoutCallingTumblebug() {
        DeploymentRequest request = nodeGroupRequest("group-a", VmDeploymentMode.CLUSTERING);

        assertThatThrownBy(() -> resolver.resolve(request))
                .hasMessageContaining("STANDALONE");
        verifyNoInteractions(cbtumblebugRestApi);
    }

    @Test
    void rejectsUnknownNodeGroup() {
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a"))
                .thenReturn(mci(vm("vm-b", null, "group-b", "Running")));

        assertThatThrownBy(() -> resolver.resolve(nodeGroupRequest("group-a", VmDeploymentMode.STANDALONE)))
                .hasMessageContaining("was not found");
    }

    @Test
    void rejectsNodeGroupWithoutRunningMembers() {
        when(cbtumblebugRestApi.getMciByMciId("project-a", "mci-a"))
                .thenReturn(mci(
                        vm("vm-a", null, "group-a", "Stopped"),
                        vm("vm-b", null, "group-a", null)));

        assertThatThrownBy(() -> resolver.resolve(nodeGroupRequest("group-a", VmDeploymentMode.STANDALONE)))
                .hasMessageContaining("no running VMs");
    }

    @Test
    void leavesLegacyVmSelectionUnchanged() {
        DeploymentRequest request = DeploymentRequest.builder()
                .deploymentType(DeploymentType.VM)
                .namespace("project-a")
                .mciId("mci-a")
                .vmIds(List.of("vm-a"))
                .vmDeploymentMode(VmDeploymentMode.STANDALONE)
                .build();

        resolver.resolve(request);

        assertThat(request.getVmIds()).containsExactly("vm-a");
        verifyNoInteractions(cbtumblebugRestApi);
    }

    @Test
    void rejectsNodeGroupOnNonVmDeployment() {
        DeploymentRequest request = DeploymentRequest.builder()
                .deploymentType(DeploymentType.K8S)
                .vmNodeGroupId("group-a")
                .build();

        assertThatThrownBy(() -> resolver.resolve(request))
                .hasMessageContaining("only be used for VM deployment");
        verifyNoInteractions(cbtumblebugRestApi);
    }

    private DeploymentRequest nodeGroupRequest(String nodeGroupId, VmDeploymentMode mode) {
        return DeploymentRequest.builder()
                .deploymentType(DeploymentType.VM)
                .namespace("project-a")
                .mciId("mci-a")
                .vmNodeGroupId(nodeGroupId)
                .vmDeploymentMode(mode)
                .build();
    }

    private MciDto mci(VmAccessInfo... vms) {
        MciDto mci = new MciDto();
        mci.setNode(List.of(vms));
        return mci;
    }

    private VmAccessInfo vm(String id, String name, String subGroupId, String status) {
        VmAccessInfo vm = new VmAccessInfo();
        vm.setId(id);
        vm.setName(name);
        vm.setSubGroupId(subGroupId);
        vm.setStatus(status);
        return vm;
    }
}
