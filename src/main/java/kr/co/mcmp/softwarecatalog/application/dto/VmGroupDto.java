package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.Data;

@Data
public class VmGroupDto {

    private String namespace;
    private String mciId;
    private String vmId;
}
