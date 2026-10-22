package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.softwarecatalog.application.model.PackageImageReference;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Connects an imported reference to the configured Nexus Registry, never to an arbitrary host. */
@Service
@RequiredArgsConstructor
public class NexusImageAccess {
    private final NexusIntegrationService nexusIntegrationService;
    private final NexusConfig nexusConfig;
    private final ObjectMapper objectMapper;

    public String deploymentReference(String pushedReference) {
        PackageImageReference.requireNexusReference(pushedReference);
        OssDto info = nexusIntegrationService.getNexusInfoFromDB();
        String internal = endpoint(nexusConfig.getRegistryEndpoint(), info).authority();
        if (!authority(pushedReference).equals(internal))
            throw new IllegalArgumentException("The imported image does not belong to the configured Nexus Registry");
        String destination = deploymentEndpoint(info).authority();
        return destination + pushedReference.substring(pushedReference.indexOf('/'));
    }

    /** Used only for the short-lived Docker config on the selected deployment VM. */
    public String dockerAuthConfig(String imageReference) {
        PackageImageReference.requireNexusReference(imageReference);
        OssDto info = nexusIntegrationService.getNexusInfoFromDB();
        String registry = authority(imageReference);
        if (!registry.equals(deploymentEndpoint(info).authority()))
            throw new IllegalArgumentException("Refusing to send Nexus credentials to another Registry");
        String password = Base64Utils.base64Decoding(info.getOssPassword());
        if (info.getOssUsername() == null || info.getOssUsername().isBlank() || password == null || password.isEmpty())
            throw new IllegalStateException("Nexus Registry credentials are unavailable");
        String auth = Base64.getEncoder().encodeToString((info.getOssUsername() + ":" + password)
                .getBytes(StandardCharsets.UTF_8));
        try {
            return objectMapper.writeValueAsString(Map.of("auths", Map.of(registry, Map.of("auth", auth))));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not prepare Nexus Registry authentication");
        }
    }

    private DockerRegistryAccess.Endpoint deploymentEndpoint(OssDto info) {
        String override = nexusConfig.getDeploymentRegistryEndpoint();
        return endpoint(override == null || override.isBlank() ? nexusConfig.getRegistryEndpoint() : override, info);
    }

    private DockerRegistryAccess.Endpoint endpoint(String override, OssDto info) {
        return DockerRegistryAccess.endpoint(override, info.getOssUrl(), nexusConfig.getDockerPort(), false);
    }

    private static String authority(String reference) { return reference.substring(0, reference.indexOf('/')); }
}
