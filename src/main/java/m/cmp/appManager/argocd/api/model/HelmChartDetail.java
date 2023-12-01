package m.cmp.appManager.argocd.api.model;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Tag(name="HelmChartDetail", description = "Helm Chart > Values.yaml 파일 내용을 가져오기 위한 객체")
public class HelmChartDetail implements Serializable {

	private static final long serialVersionUID = 1506603531962746179L;

	private Helm helm;
	private String type;
}
