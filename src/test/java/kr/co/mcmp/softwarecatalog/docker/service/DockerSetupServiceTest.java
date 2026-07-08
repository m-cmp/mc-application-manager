package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class DockerSetupServiceTest {

    private static final String NAMESPACE = "ns01";
    private static final String MCI_ID = "mci01";
    private static final String VM_ID = "vm01";

    @Mock
    private CbtumblebugRestApi cbtumblebugRestApi;

    private DockerSetupService dockerSetupService;

    @BeforeEach
    void setUp() {
        dockerSetupService = new DockerSetupService(cbtumblebugRestApi);
    }

    @Test
    void checkAndInstallDockerSkipsConfigurationWhenDockerRemoteApiAlreadyEnabled() {
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("docker --version") && command.contains("Remote API enabled")), isNull(), eq(VM_ID)))
                .thenReturn("Docker version 27.0.0\nRemote API enabled");

        dockerSetupService.checkAndInstallDocker(NAMESPACE, MCI_ID, VM_ID);

        verify(cbtumblebugRestApi, times(1))
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.contains("docker --version") && command.contains("Remote API enabled")), isNull(), eq(VM_ID));
        verify(cbtumblebugRestApi, never())
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.contains("systemctl restart docker")), isNull(), eq(VM_ID));
    }

    @Test
    void checkAndInstallDockerFailsWhenDockerServiceIsNotManagedBySystemd() {
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("docker --version") && command.contains("Remote API enabled")), isNull(), eq(VM_ID)))
                .thenReturn("Docker version 27.0.0\nRemote API not enabled");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("sudo -n true")), isNull(), eq(VM_ID)))
                .thenReturn("SUDO_OK");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("systemctl cat docker.service")), isNull(), eq(VM_ID)))
                .thenReturn("DOCKER_SERVICE_NOT_FOUND");

        assertThatThrownBy(() -> dockerSetupService.checkAndInstallDocker(NAMESPACE, MCI_ID, VM_ID))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Docker systemd service is not available");

        verify(cbtumblebugRestApi, never())
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.contains("get-docker.sh")), isNull(), eq(VM_ID));
        verify(cbtumblebugRestApi, never())
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.contains("override.conf")), isNull(), eq(VM_ID));
    }

    @Test
    void checkAndInstallDockerFailsWhenDockerRestartFailsAfterRemoteApiConfiguration() {
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("docker --version") && command.contains("Remote API enabled")), isNull(), eq(VM_ID)))
                .thenReturn("Docker version 27.0.0\nRemote API not enabled");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("sudo -n true")), isNull(), eq(VM_ID)))
                .thenReturn("SUDO_OK");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("systemctl cat docker.service")), isNull(), eq(VM_ID)))
                .thenReturn("DOCKER_SERVICE_FOUND");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("override.conf")), isNull(), eq(VM_ID)))
                .thenReturn("DOCKER_REMOTE_CONFIGURED");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("chmod 666")), isNull(), eq(VM_ID)))
                .thenReturn("permissions updated");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("bridge-nf-call-iptables")), isNull(), eq(VM_ID)))
                .thenReturn("sysctl updated");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("vm.max_map_count")), isNull(), eq(VM_ID)))
                .thenReturn("vm.max_map_count=262144");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("PubkeyAcceptedAlgorithms")), isNull(), eq(VM_ID)))
                .thenReturn("sshd restarted");
        when(cbtumblebugRestApi.executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                command.contains("systemctl restart docker")), isNull(), eq(VM_ID)))
                .thenReturn("Failed to restart docker.service: Unit docker.service not found.\nDOCKER_RESTART_FAILED");

        assertThatThrownBy(() -> dockerSetupService.checkAndInstallDocker(NAMESPACE, MCI_ID, VM_ID))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Failed to restart Docker service");

        verify(cbtumblebugRestApi, never())
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.contains("get-docker.sh")), isNull(), eq(VM_ID));
        verify(cbtumblebugRestApi, never())
                .executeMciCommand(eq(NAMESPACE), eq(MCI_ID), argThat(command ->
                        command.startsWith("ps aux | grep dockerd")), isNull(), eq(VM_ID));
    }
}
