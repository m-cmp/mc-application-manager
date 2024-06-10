package m.cmp.appManager.jenkins.pipeline.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Tag(name = "Pipeline", description = "Jenkins Pipeline 정보")
public class Pipeline implements Serializable {

	private static final long serialVersionUID = -5938410670102351098L;

	private Integer pipelineId;				// pipeline 일련번호 
	private String  pipelineCd;				// pipeline 타입 코드
	private String  pipelineCdName;			// pipeline 타입 코드명  
	private String 	pipelineName;			// pipeline 명  
	private String  pipelineScript;			// pipeline 스크립트 
	
	private Integer pipelineOrder;			// 빌드 별 Pipeline 스크립트 순서

    private String  regId;
    private String  regName;
    private String  regDate;
    private String  modId;
    private String  modName;
    private String  modDate;
}
