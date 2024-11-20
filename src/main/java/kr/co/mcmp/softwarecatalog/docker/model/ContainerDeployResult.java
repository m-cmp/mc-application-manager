package kr.co.mcmp.softwarecatalog.docker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerDeployResult {

    private String containerId;
    private String deploymentResult;
    private boolean success;

}
