package kr.co.mcmp.softwarecatalog.docker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DockerHostResourceInfo {

    private final Integer cpuCores;
    private final Double memoryGb;
}
