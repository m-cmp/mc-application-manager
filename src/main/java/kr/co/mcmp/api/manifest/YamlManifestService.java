package kr.co.mcmp.api.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import kr.co.mcmp.api.manifest.k8s.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class YamlManifestService {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public String generateYamlDeployments(K8SDeploymentsDto deployments) {
        return generateYaml(deployments);
    }

    public String generateYamlService(K8SServiceDto service) {
        return generateYaml(service);
    }

    public String generateYamlConfigMap(K8SConfigMapDto configMap) {
        return generateYaml(configMap);
    }

    public String generateYamlPod(K8SPodDto pod) {
        return generateYaml(pod);
    }

    public String generateYamlHpa(K8SHpaDto hpa) {
        return generateYaml(hpa);
    }

    private <T> String generateYaml(T dto) {
        try {
            String yamlText = mapper.writeValueAsString(dto);
            log.info("Text: {}", yamlText);
            return yamlText;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Yaml Generation Failed", e);
        }
    }

/*    private String removeQuotesYaml(String yamlText) {
        yamlText = yamlText.replaceAll(": \"([^\"]*)\"", ": $1");
        yamlText = yamlText.replaceAll(": \"(\\d+)\"", ": $1");
        return yamlText;
    }*/

/*    private void saveYaml(String yamlText) {
        CommonUploadComponent.TextComponentDto textComponent = CommonUploadComponent.TextComponentDto.builder()
                .filename("test")
                .directory("/")
                .text(yamlText)
                .build();
        moduleComponentService.createComponentByText("nexus", "repo", textComponent);
    }*/
}
