package kr.co.mcmp.softwarecatalog.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.*;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.*;
import kr.co.mcmp.softwarecatalog.application.repository.VmClusterFirewallRuleRepository;
import kr.co.mcmp.softwarecatalog.application.repository.VmSecurityGroupExposureRepository;

@ExtendWith(MockitoExtension.class)
class ElasticsearchClusterNetworkServiceTest {
    @Mock CbtumblebugRestApi tumblebug;
    @Mock VmClusterFirewallRuleRepository repository;
    @Mock VmSecurityGroupExposureRepository services;
    ElasticsearchClusterNetworkService service;
    final List<VmClusterFirewallRule> rows = new ArrayList<>();
    final Set<String> firewall = new HashSet<>();
    final AtomicLong sequence = new AtomicLong();
    MciDto infra;

    @BeforeEach void setup() {
        service = new ElasticsearchClusterNetworkService(tumblebug, repository, services);
        infra = new MciDto(); infra.setId("infra");
        infra.setNode(new ArrayList<>(List.of(vm("vm1", "192.0.2.1"), vm("vm2", "192.0.2.2"))));
        lenient().when(tumblebug.getMcisByNamespace("ns")).thenReturn(List.of(infra));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(i -> {
            VmClusterFirewallRule r = i.getArgument(0);
            if (r.getId() == null) { r.setId(sequence.incrementAndGet()); rows.add(r); }
            return r;
        });
        lenient().when(repository.findByDeploymentIdAndReleasedAtIsNull(anyLong())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getDeploymentId().equals(i.getArgument(0)) && r.getReleasedAt() == null).toList());
        lenient().when(repository.findByNamespaceAndSecurityGroupIdAndAllowedCidrAndReleasedAtIsNull(anyString(), anyString(), anyString()))
                .thenAnswer(i -> rows.stream().filter(r -> r.getNamespace().equals(i.getArgument(0))
                        && r.getSecurityGroupId().equals(i.getArgument(1)) && r.getAllowedCidr().equals(i.getArgument(2))
                        && r.getReleasedAt() == null).toList());
        lenient().when(tumblebug.hasInboundTcpFirewallRule(eq("ns"), anyString(), eq(9300), anyString()))
                .thenAnswer(i -> firewall.contains(i.getArgument(1) + ":" + i.getArgument(3)));
        lenient().when(tumblebug.addInboundTcpFirewallRule(eq("ns"), anyString(), eq(9300), anyString()))
                .thenAnswer(i -> { firewall.add(i.getArgument(1) + ":" + i.getArgument(3)); return "ok"; });
        lenient().when(tumblebug.deleteInboundTcpFirewallRule(eq("ns"), anyString(), eq(9300), anyString()))
                .thenAnswer(i -> { firewall.remove(i.getArgument(1) + ":" + i.getArgument(3)); return "ok"; });
    }

    @Test void standaloneDoesNotReadOrChangeNetwork() {
        var request = request(); request.setVmDeploymentMode(VmDeploymentMode.STANDALONE);
        service.prepare(request, history(1L, "vm1"));
        verifyNoInteractions(tumblebug, repository);
    }

    @Test void addsOnlyPeer32TransportRulesAndIsIdempotent() {
        service.prepare(request(), history(1L, "vm1"));
        service.prepare(request(), history(2L, "vm2"));
        service.prepare(request(), history(1L, "vm1"));
        assertThat(firewall).containsExactlyInAnyOrder("sg:192.0.2.1/32", "sg:192.0.2.2/32");
        verify(tumblebug, times(2)).addInboundTcpFirewallRule(eq("ns"), eq("sg"), eq(9300), anyString());
        service.release(1L); service.release(2L);
        assertThat(firewall).isEmpty();
        assertThat(rows).allMatch(r -> r.getReleasedAt() != null);
    }

    @Test void preservesPreExistingPeerRuleAndUnrelatedRules() {
        firewall.addAll(List.of("sg:192.0.2.2/32", "ssh-existing", "service-existing"));
        service.prepare(request(), history(1L, "vm1")); service.release(1L);
        assertThat(firewall).containsExactlyInAnyOrder("sg:192.0.2.2/32", "ssh-existing", "service-existing");
        verify(tumblebug, never()).addInboundTcpFirewallRule(any(), any(), anyInt(), any());
        verify(tumblebug, never()).deleteInboundTcpFirewallRule(any(), any(), anyInt(), any());
    }

    @Test void preservesSharedRuleUntilLastDeploymentIsReleased() {
        infra.getNode().add(vm("vm3", "192.0.2.3"));
        var request = request(); request.setVmIds(List.of("vm1", "vm2", "vm3"));
        service.prepare(request, history(1L, "vm1"));
        service.prepare(request, history(3L, "vm3"));
        service.release(1L);
        assertThat(firewall).contains("sg:192.0.2.2/32").doesNotContain("sg:192.0.2.3/32");
        service.release(3L);
        assertThat(firewall).isEmpty();
        verify(tumblebug, times(1)).deleteInboundTcpFirewallRule("ns", "sg", 9300, "192.0.2.2/32");
    }

    @Test void refusesGroupSharedWithUnselectedVmBeforeAnyMutation() {
        infra.getNode().add(vm("unselected", "192.0.2.3"));
        assertThatThrownBy(() -> service.prepare(request(), history(1L, "vm1"))).hasMessageContaining("unselected VM");
        verify(tumblebug, never()).addInboundTcpFirewallRule(any(), any(), anyInt(), any());
        assertThat(rows).isEmpty();
    }

    @Test void refusesGroupSharedAcrossInfras() {
        var other = new MciDto(); other.setId("another-infra"); other.setNode(List.of(vm("vm1", "192.0.2.9")));
        when(tumblebug.getMcisByNamespace("ns")).thenReturn(List.of(infra, other));
        assertThatThrownBy(() -> service.prepare(request(), history(1L, "vm1"))).hasMessageContaining("unselected VM");
        assertThat(rows).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"", "0.0.0.0", "127.0.0.1", "224.0.0.1", "169.254.1.1", "192.0.2.1/24", "hostname", "192.0.2.256"})
    void rejectsInvalidOrBroadPeerAddressesWithoutChangingRules(String ip) {
        infra.getNode().get(1).setPublicIP(ip);
        assertThatThrownBy(() -> service.prepare(request(), history(1L, "vm1"))).isInstanceOf(RuntimeException.class);
        verify(tumblebug, never()).addInboundTcpFirewallRule(any(), any(), anyInt(), any());
        assertThat(rows).isEmpty();
    }

    @Test void refusesAmbiguousSecurityGroups() {
        infra.getNode().get(1).setSecurityGroupIds(List.of("sg", "sg2"));
        assertThatThrownBy(() -> service.prepare(request(), history(1L, "vm1"))).hasMessageContaining("exactly one Security Group");
        assertThat(rows).isEmpty();
    }

    @Test void missingVmFailsBeforeMutation() {
        infra.setNode(List.of(vm("vm1", "192.0.2.1")));
        assertThatThrownBy(() -> service.prepare(request(), history(1L, "vm1"))).hasMessageContaining("not found");
        assertThat(rows).isEmpty();
    }

    @Test void rollsBackEarlierManagedRuleWhenLaterAddIsDenied() {
        infra.getNode().add(vm("vm3", "192.0.2.3"));
        var request = request(); request.setVmIds(List.of("vm1", "vm2", "vm3"));
        doThrow(new IllegalStateException("Permission denied")).when(tumblebug)
                .addInboundTcpFirewallRule("ns", "sg", 9300, "192.0.2.3/32");
        assertThatThrownBy(() -> service.prepare(request, history(1L, "vm1"))).hasMessageContaining("Permission denied");
        assertThat(firewall).isEmpty();
        assertThat(rows).allMatch(r -> r.getReleasedAt() != null);
    }

    @Test void failedCleanupRemainsRetryable() {
        service.prepare(request(), history(1L, "vm1"));
        doThrow(new IllegalStateException("Unavailable")).when(tumblebug)
                .deleteInboundTcpFirewallRule("ns", "sg", 9300, "192.0.2.2/32");
        assertThatThrownBy(() -> service.release(1L)).hasMessageContaining("Unavailable");
        assertThat(rows.get(0).getReleasedAt()).isNull();
    }

    @Test void clusterCleanupPreservesRuleUsedAsAnotherApplicationsServicePort() {
        service.prepare(request(), history(1L, "vm1"));
        var exposure = VmSecurityGroupExposure.builder().id(10L).managedByApplicationManager(false).build();
        when(services.findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                "ns", "sg", 9300, "192.0.2.2/32")).thenReturn(List.of(exposure));
        service.release(1L);
        assertThat(firewall).contains("sg:192.0.2.2/32");
        assertThat(exposure.getManagedByApplicationManager()).isTrue();
    }

    @Test void servicePortCleanupTransfersOwnershipToActiveCluster() {
        firewall.add("sg:192.0.2.2/32");
        service.prepare(request(), history(1L, "vm1"));
        assertThat(rows.get(0).isManaged()).isFalse();
        assertThat(service.retainIfRequired("ns", "sg", 9200, "192.0.2.2/32")).isFalse();
        assertThat(service.retainIfRequired("ns", "sg", 9300, "192.0.2.2/32")).isTrue();
        service.release(1L);
        assertThat(firewall).isEmpty();
    }

    private DeploymentRequest request() {
        return DeploymentRequest.builder().namespace("ns").mciId("infra").vmIds(List.of("vm1", "vm2"))
                .vmDeploymentMode(VmDeploymentMode.CLUSTERING).build();
    }
    private DeploymentHistory history(Long id, String vm) { return DeploymentHistory.builder().id(id).vmId(vm).build(); }
    private VmAccessInfo vm(String id, String ip) {
        var vm = new VmAccessInfo(); vm.setId(id); vm.setPublicIP(ip); vm.setSecurityGroupIds(List.of("sg")); vm.setStatus("Running"); return vm;
    }
}
