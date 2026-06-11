package kr.co.mcmp.softwarecatalog.application.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.application.constants.LogType;
import kr.co.mcmp.softwarecatalog.application.model.DeploymentLog;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeploymentLogDTO {

    private Long id;
    private Long deploymentId;
    private LogType logType;
    private String logMessage;
    private LocalDateTime loggedAt;

    public DeploymentLogDTO(DeploymentLog entity) {
        this.id = entity.getId();
        this.deploymentId = entity.getDeployment() != null ? entity.getDeployment().getId() : null;
        this.logType = entity.getLogType();
        this.logMessage = entity.getLogMessage();
        this.loggedAt = entity.getLoggedAt();
    }
}
