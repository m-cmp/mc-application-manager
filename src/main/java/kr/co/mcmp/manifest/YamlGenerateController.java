package kr.co.mcmp.manifest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="yaml generate", description="각 yaml을 조립해줌")
@RestController
@RequestMapping("/yaml")
public class YamlGenerateController {

    Logger logger = LoggerFactory.getLogger(YamlGenerateController.class);

    @Autowired
    YamlGenerateService yamlGenerateService;

    @Operation(summary = "yaml generate for pod")
    @PostMapping("/pod")
    public String generatePodYaml(){
        return null;
    }

    @Operation(summary = "yaml generate for deployment")
    @PostMapping("/deployment")
    public String generateDeploymentYaml(){
        return null;
    }

    @Operation(summary = "yaml generate for service")
    @PostMapping("/service")
    public String generateServiceYaml(){
        return null;
    }

    @Operation(summary = "yaml generate for configmap")
    @PostMapping("/configmap")
    public String generateConfigmapYaml(){
        return null;
    }


}
