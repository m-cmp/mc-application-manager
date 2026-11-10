package kr.co.mcmp.softwarecatalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentHistory;
import kr.co.mcmp.softwarecatalog.application.model.VmSecurityGroupExposure;
import kr.co.mcmp.softwarecatalog.application.repository.VmSecurityGroupExposureRepository;

@ExtendWith(MockitoExtension.class)
class VmSecurityGroupExposureServiceTest {

    @Mock
    private CbtumblebugRestApi cbtumblebugRestApi;
    @Mock
    private VmSecurityGroupExposureRepository repository;

    private VmSecurityGroupExposureService service;

    @BeforeEach
    void setUp() {
        service = new VmSecurityGroupExposureService(cbtumblebugRestApi, repository,
                org.mockito.Mockito.mock(ElasticsearchClusterNetworkService.class));
        lenient().when(repository.saveAndFlush(any(VmSecurityGroupExposure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void leavesSecurityGroupUntouchedByDefault() {
        DeploymentRequest request = DeploymentRequest.builder().build();

        service.addRestrictedInboundRule(request, null, null);

        verify(cbtumblebugRestApi, never())
                .addInboundTcpFirewallRule(any(), any(),
                        org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void addsAndTracksOnlyTheRequestedTcpRule() {
        DeploymentRequest request = directAccessRequest("203.0.113.10/32");
        VmAccessInfo vm = vmWithSecurityGroups("vm01", List.of("sg01"));
        DeploymentHistory history = DeploymentHistory.builder().id(42L).build();
        when(cbtumblebugRestApi.hasInboundTcpFirewallRule(
                "ns01", "sg01", 8888, "203.0.113.10/32")).thenReturn(false);

        service.addRestrictedInboundRule(request, vm, history);

        verify(cbtumblebugRestApi)
                .addInboundTcpFirewallRule("ns01", "sg01", 8888, "203.0.113.10/32");
        ArgumentCaptor<VmSecurityGroupExposure> exposureCaptor =
                ArgumentCaptor.forClass(VmSecurityGroupExposure.class);
        verify(repository, atLeastOnce()).saveAndFlush(exposureCaptor.capture());
        VmSecurityGroupExposure exposure = exposureCaptor.getValue();
        assertThat(exposure.getDeploymentId()).isEqualTo(42L);
        assertThat(exposure.getManagedByApplicationManager()).isTrue();
        assertThat(exposure.getProvisionedAt()).isNotNull();
    }

    @Test
    void preservesAUserRuleThatAlreadyExistedBeforeDeployment() {
        DeploymentRequest request = directAccessRequest("203.0.113.10/32");
        VmAccessInfo vm = vmWithSecurityGroups("vm01", List.of("sg01"));
        DeploymentHistory history = DeploymentHistory.builder().id(42L).build();
        when(cbtumblebugRestApi.hasInboundTcpFirewallRule(
                "ns01", "sg01", 8888, "203.0.113.10/32")).thenReturn(true);

        service.addRestrictedInboundRule(request, vm, history);

        verify(cbtumblebugRestApi, never())
                .addInboundTcpFirewallRule(any(), any(),
                        org.mockito.ArgumentMatchers.anyInt(), any());
        ArgumentCaptor<VmSecurityGroupExposure> exposureCaptor =
                ArgumentCaptor.forClass(VmSecurityGroupExposure.class);
        verify(repository, atLeastOnce()).saveAndFlush(exposureCaptor.capture());
        assertThat(exposureCaptor.getValue().getManagedByApplicationManager()).isFalse();
    }

    @Test
    void removesManagedRuleWhenLastDeploymentIsReleased() {
        VmSecurityGroupExposure exposure = exposure(1L, 42L, true);
        when(repository.findByDeploymentIdAndReleasedAtIsNull(42L)).thenReturn(Optional.of(exposure));
        when(repository.findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                "ns01", "sg01", 8888, "203.0.113.10/32")).thenReturn(List.of(exposure));

        service.releaseRestrictedInboundRule(42L);

        verify(cbtumblebugRestApi)
                .deleteInboundTcpFirewallRule("ns01", "sg01", 8888, "203.0.113.10/32");
        assertThat(exposure.getReleasedAt()).isNotNull();
        assertThat(exposure.getRuleRemovedAt()).isNotNull();
    }

    @Test
    void keepsSharedRuleAndTransfersOwnershipToRemainingDeployment() {
        VmSecurityGroupExposure current = exposure(1L, 42L, true);
        VmSecurityGroupExposure remaining = exposure(2L, 43L, false);
        when(repository.findByDeploymentIdAndReleasedAtIsNull(42L)).thenReturn(Optional.of(current));
        when(repository.findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                "ns01", "sg01", 8888, "203.0.113.10/32")).thenReturn(List.of(current, remaining));

        service.releaseRestrictedInboundRule(42L);

        verify(cbtumblebugRestApi, never())
                .deleteInboundTcpFirewallRule(any(), any(),
                        org.mockito.ArgumentMatchers.anyInt(), any());
        assertThat(remaining.getManagedByApplicationManager()).isTrue();
        assertThat(current.getReleasedAt()).isNotNull();
    }

    @Test
    void doesNotDeletePreExistingRuleWhenDeploymentIsReleased() {
        VmSecurityGroupExposure exposure = exposure(1L, 42L, false);
        when(repository.findByDeploymentIdAndReleasedAtIsNull(42L)).thenReturn(Optional.of(exposure));
        when(repository.findByNamespaceAndSecurityGroupIdAndServicePortAndAllowedCidrAndReleasedAtIsNull(
                "ns01", "sg01", 8888, "203.0.113.10/32")).thenReturn(List.of(exposure));

        service.releaseRestrictedInboundRule(42L);

        verify(cbtumblebugRestApi, never())
                .deleteInboundTcpFirewallRule(any(), any(),
                        org.mockito.ArgumentMatchers.anyInt(), any());
        assertThat(exposure.getReleasedAt()).isNotNull();
        assertThat(exposure.getRuleRemovedAt()).isNull();
    }

    @Test
    void refusesToChooseArbitrarilyBetweenMultipleGroups() {
        DeploymentRequest request = directAccessRequest("203.0.113.10/32");
        VmAccessInfo vm = vmWithSecurityGroups("vm01", List.of("sg01", "sg02"));

        assertThatThrownBy(() -> service.addRestrictedInboundRule(
                request, vm, DeploymentHistory.builder().id(42L).build()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("multiple Security Groups");
    }

    @Test
    void rejectsPublicAnyCidr() {
        DeploymentRequest request = directAccessRequest("0.0.0.0/0");
        VmAccessInfo vm = vmWithSecurityGroups("vm01", List.of("sg01"));

        assertThatThrownBy(() -> service.addRestrictedInboundRule(
                request, vm, DeploymentHistory.builder().id(42L).build()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Public any CIDR");
    }

    private DeploymentRequest directAccessRequest(String cidr) {
        return DeploymentRequest.builder()
                .namespace("ns01")
                .servicePort(8888)
                .openServicePort(true)
                .servicePortCidr(cidr)
                .build();
    }

    private VmAccessInfo vmWithSecurityGroups(String vmId, List<String> securityGroupIds) {
        VmAccessInfo vm = new VmAccessInfo();
        vm.setId(vmId);
        vm.setSecurityGroupIds(securityGroupIds);
        return vm;
    }

    private VmSecurityGroupExposure exposure(Long id, Long deploymentId, boolean managed) {
        return VmSecurityGroupExposure.builder()
                .id(id)
                .deploymentId(deploymentId)
                .vmId("vm01")
                .namespace("ns01")
                .securityGroupId("sg01")
                .servicePort(8888)
                .allowedCidr("203.0.113.10/32")
                .managedByApplicationManager(managed)
                .build();
    }
}
