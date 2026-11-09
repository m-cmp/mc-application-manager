package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;

class VmInfraStateServiceTest {
    final CbtumblebugRestApi api = mock(CbtumblebugRestApi.class);
    final VmInfraStateService service = new VmInfraStateService(api);

    @Test void loadsEachInfraOnceAndUsesIndividualVmStateNotAggregate() {
        MciDto infra = infra("m1", "Suspended:1 (R:1/2)", vm("v1", "Running"), vm("v2", " Suspended "));
        when(api.getMciByMciId("p1", "m1")).thenReturn(infra);
        service.refresh(List.of(app("p1", "m1", "v1"), app("p1", "m1", "v2")));
        assertThat(VmInfraStateService.isSuspended(service.findVm("p1", "m1", "v1"))).isFalse();
        assertThat(VmInfraStateService.isSuspended(service.findVm("p1", "m1", "v2"))).isTrue();
        verify(api, times(1)).getMciByMciId("p1", "m1");
        assertThat(service.findVm("p2", "m1", "v2")).isNull();
        verifyNoMoreInteractions(api);
    }

    @ParameterizedTest @ValueSource(ints = {404, 500})
    void onlyAnExplicitResourceMissingMessageMarksInfraDeleted(int code) {
        when(api.getMciByMciId("p1", "m1")).thenThrow(
                new CbtumblebugException(code, "{\"message\":\"The infra m1 does not exist.\"}"));
        service.refresh(List.of(app("p1", "m1", "v1")));
        assertThat(service.isDeleted("p1", "m1")).isTrue();
        assertThat(service.isDeleted("p2", "m1")).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500, 502, 504})
    void errorsNeverHideRecordsAndInvalidatePreviouslyDeletedSnapshot(int code) {
        when(api.getMciByMciId("p1", "m1")).thenThrow(
                new CbtumblebugException(500, "{\"message\":\"The infra m1 does not exist.\"}"));
        service.refresh(List.of(app("p1", "m1", "v1")));
        doThrow(new CbtumblebugException(code, "upstream unavailable")).when(api).getMciByMciId("p1", "m1");
        service.refresh(List.of(app("p1", "m1", "v1")));
        assertThat(service.isDeleted("p1", "m1")).isFalse();
    }

    @Test void emptyOrWrongTargetResponseIsUnknownRatherThanDeleted() {
        when(api.getMciByMciId("p1", "m1")).thenReturn(null, infra("other", "Running"));
        for (int i = 0; i < 2; i++) {
            service.refresh(List.of(app("p1", "m1", "v1")));
            assertThat(service.isDeleted("p1", "m1")).isFalse();
        }
    }

    @Test void actionChecksFreshStateEvenWhenCachedVmWasRunning() {
        when(api.getMciByMciId("p1", "m1")).thenReturn(
                infra("m1", "Running", vm("v1", "Running")),
                infra("m1", "Suspended", vm("v1", "Suspended")));
        service.refresh(List.of(app("p1", "m1", "v1")));
        assertThatThrownBy(() -> service.assertActionAllowed(app("p1", "m1", "v1"), ActionType.RESTART))
                .hasMessageContaining("Suspended");
    }

    @Test void blocksAllThreeActionsWhileSuspendedButAllowsThemAfterResume() {
        for (ActionType action : List.of(ActionType.RESTART, ActionType.STOP, ActionType.UNINSTALL)) {
            when(api.getMciByMciId("p1", "m1")).thenReturn(infra("m1", "Suspended", vm("v1", "suspended")));
            assertThatThrownBy(() -> service.assertActionAllowed(app("p1", "m1", "v1"), action))
                    .hasMessageContaining("Suspended");
            when(api.getMciByMciId("p1", "m1")).thenReturn(infra("m1", "Running", vm("v1", "Running")));
            assertThatCode(() -> service.assertActionAllowed(app("p1", "m1", "v1"), action)).doesNotThrowAnyException();
        }
    }

    @Test void refusesUnknownOrMissingVmWithoutSendingDockerCommand() {
        assertThatThrownBy(() -> service.assertActionAllowed(app("p1", "m1", "v1"), ActionType.STOP))
                .hasMessageContaining("Unable to verify");
        when(api.getMciByMciId("p1", "m1")).thenReturn(infra("m1", "Running"));
        assertThatThrownBy(() -> service.assertActionAllowed(app("p1", "m1", "v1"), ActionType.UNINSTALL))
                .hasMessageContaining("VM was not found");
    }

    static ApplicationStatus app(String project, String infra, String vm) {
        return ApplicationStatus.builder().namespace(project).mciId(infra).vmId(vm)
                .deploymentType(DeploymentType.VM).build();
    }
    static VmAccessInfo vm(String id, String status) {
        VmAccessInfo vm = new VmAccessInfo(); vm.setId(id); vm.setStatus(status); vm.setSubGroupId("g1"); return vm;
    }
    static MciDto infra(String id, String status, VmAccessInfo... nodes) {
        MciDto infra = new MciDto(); infra.setId(id); infra.setStatus(status); infra.setNode(List.of(nodes)); return infra;
    }
}
