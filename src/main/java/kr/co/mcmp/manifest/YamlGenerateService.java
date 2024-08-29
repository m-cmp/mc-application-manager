package kr.co.mcmp.manifest;

import io.kubernetes.client.openapi.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class YamlGenerateService {

    Logger logger = LoggerFactory.getLogger(YamlGenerateService.class);

    @Autowired
    K8SDeployYamlGenerator yamlGen;


    public String generatePodYaml(K8SPodDTO podContents){
        return yamlGen.getPodYaml(podContents);
    }

    public String generateDeploymentYaml(K8SDeploymentDTO deploy){
        return yamlGen.getDeploymentYaml(deploy);
    }

    public String generateConfigmapYaml(){
        return "";
    }

    public String generateHPAYaml(){
        return "";
    }

    public String generateServiceYaml(){
        return "";
    }


}
