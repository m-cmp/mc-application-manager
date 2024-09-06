package kr.co.mcmp.manifest;

import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class YamlGenerateService {

    Logger logger = LoggerFactory.getLogger(YamlGenerateService.class);

//    @Autowired
//    K8SDeployYamlGenerator yamlGen;


    public String generatePodYaml(K8SPodDTO podContents){
        //return yamlGen.getPodYaml(podContents);
        return getPodYaml(podContents);
    }

    public String generateDeploymentYaml(K8SDeploymentDTO deploy){
        //return yamlGen.getDeploymentYaml(deploy);
        return "";
    }

    public String generateConfigmapYaml(){
        return "";
    }

    public String generateHPAYaml(K8SHPADTO hpaDto){
        //return yamlGen.getHpaYaml(hpaDto);
        return "";
    }

    public String generateServiceYaml(){
        return "";
    }





    private V1Pod getPod(K8SPodDTO podDto) {

        V1Pod pod = new V1Pod();
        pod.setApiVersion(K8S.Controller.Pod.getApiVersion());
        pod.setKind("Pod");

        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(podDto.getPodName());
        metadata.setNamespace(podDto.getNamespace());
        metadata.setLabels(podDto.getLabels());
        pod.setMetadata(metadata);

        V1PodSpec podSpec = getPodSpec(podDto);
        pod.setSpec(podSpec);

        return pod;
    }

    private V1PodSpec getPodSpec(K8SPodDTO podDto){
        V1PodSpec podSpec = new V1PodSpec();
        List<V1Container> containerList = new ArrayList<>(); //podDto.getContainers();
        for(K8SPodDTO.Container cont: podDto.getContainers()){
            V1Container container = new V1Container();
            container.setName(cont.getContainerName());
            container.setImage(cont.getContainerImage());
            List<V1ContainerPort> portList = new ArrayList<>();
            for(K8SPodDTO.Port pt: cont.getPorts()) {
                V1ContainerPort port = new V1ContainerPort();
                port.setProtocol(pt.getProtocol());
                port.setProtocol(pt.getName());
                port.setContainerPort(pt.getContainerPort());
                portList.add(port);
            }
            //V1ResourceRequirements resourceReq = new V1ResourceRequirements();
            //resourceReq.setLimits();
            container.setPorts(portList);
            containerList.add(container);
        }
        podSpec.setContainers(containerList);
        podSpec.setRestartPolicy(podDto.getRestartPolicy());
        return podSpec;
    }


    public String getPodYaml(K8SPodDTO podDto) {
        V1Pod pod = getPod(podDto);
        StringBuffer buffer = new StringBuffer();
        buffer.append(Yaml.dump(pod));
        String yaml = buffer.toString();
        return yaml;
    }




}
