package kr.co.mcmp.softwarecatalog.docker.service;

import org.springframework.stereotype.Service;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class DockerSetupService {

    private static final Logger log = LoggerFactory.getLogger(DockerSetupService.class);

    private final CbtumblebugRestApi cbtumblebugRestApi;

    public void checkAndInstallDocker(String namespace, String mciId, String vmId) throws ApplicationException {
        log.info("Checking Docker installation for namespace: {}, mciId: {}, vmId: {}", namespace, mciId, vmId);
        String checkDockerCommand = "docker --version && ps aux | grep dockerd | grep -q -- '-H tcp://0.0.0.0:2375' && echo 'Remote API enabled' || echo 'Remote API not enabled'";
        try {
            String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, checkDockerCommand, null, vmId);
            log.info("Docker check result: '{}'", result);
            
            if (result == null || result.trim().isEmpty()) {
                log.warn("Docker check command returned empty result. Assuming Docker needs to be installed...");
                installAndConfigureDockerOnVM(namespace, mciId, vmId);
            } else if (!result.contains("Docker version") || !result.contains("Remote API enabled")) {
                log.warn("Docker is not installed or remote API is not enabled. Installing/configuring Docker...");
                installAndConfigureDockerOnVM(namespace, mciId, vmId);
            } else {
                log.info("Docker is already installed and configured for remote access on VM: {}", vmId);
            }
        } catch (Exception e) {
            log.error("Error checking Docker installation", e);
            throw new ApplicationException("Failed to check Docker installation: " + e.getMessage());
        }
    }

    private void installAndConfigureDockerOnVM(String namespace, String mciId, String vmId) throws ApplicationException {
        log.info("Starting Docker installation and configuration for namespace: {}, mciId: {}, vmId: {}", namespace, mciId, vmId);
        try {
            installDocker(namespace, mciId, vmId);
            configureDockerForRemoteAccess(namespace, mciId, vmId);
            setDockerPermissions(namespace, mciId, vmId);
            enableBridgeNfCallIptables(namespace, mciId, vmId);
            configureSSHForDockerAccess(namespace, mciId, vmId);
            restartDocker(namespace, mciId, vmId);
            verifyDockerConfiguration(namespace, mciId, vmId);
        } catch (Exception e) {
            log.error("Error installing or configuring Docker", e);
            throw new ApplicationException("Failed to install or configure Docker: " + e.getMessage());
        }
    }

    
    private void installDocker(String namespace, String mciId, String vmId) throws Exception {
        String installDockerCommand = 
            "curl -fsSL https://get.docker.com -o get-docker.sh && " +
            "sudo sh get-docker.sh && " +
            "sudo usermod -aG docker $USER && " +
            "sudo systemctl enable docker";
        
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, installDockerCommand, null, vmId);
        log.info("Docker installation result: {}", result);
    }

    private void configureDockerForRemoteAccess(String namespace, String mciId, String vmId) throws Exception {
        String configureDockerCommand = 
            "sudo mkdir -p /etc/systemd/system/docker.service.d && " +
            "echo '[Service]' | sudo tee /etc/systemd/system/docker.service.d/override.conf && " +
            "echo 'ExecStart=' | sudo tee -a /etc/systemd/system/docker.service.d/override.conf && " +
            "echo 'ExecStart=/usr/bin/dockerd -H fd:// -H tcp://0.0.0.0:2375 -H unix:///var/run/docker.sock' | sudo tee -a /etc/systemd/system/docker.service.d/override.conf && " +
            "sudo systemctl daemon-reload";
        
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, configureDockerCommand, null, vmId);
        log.info("Docker remote access configuration result: {}", result);
    }

    private void configureSSHForDockerAccess(String namespace, String mciId, String vmId) throws Exception {
        String configureSSHCommand = 
            "sudo sed -i 's/^#*PubkeyAcceptedAlgorithms.*/PubkeyAcceptedAlgorithms=+ssh-rsa/' /etc/ssh/sshd_config && " +
            "sudo systemctl restart sshd";
        
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, configureSSHCommand, null, vmId);
        log.info("SSH configuration for Docker access result: {}", result);
    }

    private void setDockerPermissions(String namespace, String mciId, String vmId) throws Exception {
        String setPermissionsCommand = "sudo chmod 666 /var/run/docker.sock";
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, setPermissionsCommand, null, vmId);
        log.info("Docker permissions setting result: {}", result);
    }

    private void enableBridgeNfCallIptables(String namespace, String mciId, String vmId) throws Exception {
        String command = 
            "echo 'net.bridge.bridge-nf-call-iptables = 1' | sudo tee -a /etc/sysctl.conf && " +
            "echo 'net.bridge.bridge-nf-call-ip6tables = 1' | sudo tee -a /etc/sysctl.conf && " +
            "sudo sysctl -p";
        
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, command, null, vmId);
        log.info("Bridge-nf-call-iptables configuration result: {}", result);
    }

    private void restartDocker(String namespace, String mciId, String vmId) throws Exception {
        String restartCommand = "sudo systemctl restart docker";
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, restartCommand, null, vmId);
        log.info("Docker restart result: {}", result);
    }

    private void verifyDockerConfiguration(String namespace, String mciId, String vmId) throws Exception {
        String verifyCommand = "ps aux | grep dockerd | grep -q -- '-H tcp://0.0.0.0:2375' && echo 'Remote API enabled' || echo 'Remote API not enabled'";
        String result = cbtumblebugRestApi.executeMciCommand(namespace, mciId, verifyCommand, null, vmId);
        
        log.info("Docker verification result: '{}'", result);
        
        if (result == null || result.trim().isEmpty()) {
            log.warn("Docker verification command returned empty result. This might indicate the command didn't execute properly.");
            // 빈 결과인 경우에도 Docker가 실행 중인지 확인해보자
            String alternativeCommand = "systemctl is-active docker && echo 'Docker service is active' || echo 'Docker service is not active'";
            String alternativeResult = cbtumblebugRestApi.executeMciCommand(namespace, mciId, alternativeCommand, null, vmId);
            log.info("Alternative Docker check result: '{}'", alternativeResult);
            
            if (!alternativeResult.contains("Docker service is active")) {
                throw new ApplicationException("Failed to verify Docker configuration - Docker service is not active");
            }
        } else if (!result.contains("Remote API enabled")) {
            log.warn("Docker remote API is not enabled. Result: '{}'", result);
            throw new ApplicationException("Failed to configure Docker for remote access. Result: " + result);
        }
        
        log.info("Docker configuration verified successfully");
    }
}