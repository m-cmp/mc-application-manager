package m.cmp.appManager.k8s.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.argocd.model.ArgocdConfig;
import m.cmp.appManager.argocd.service.ArgocdService;
import m.cmp.appManager.k8s.model.K8SConfig;
import m.cmp.appManager.k8s.service.K8SService;
import m.cmp.appManager.util.AES256Util;
import m.cmp.appManager.util.Base64Utils;

@Tag(name = "k8s", description = "k8s config 설정 (ArgoCd)")
@RestController
public class K8SController {
	@Autowired
	private K8SService k8sService;

	@Autowired
	private ArgocdService argocdService;

	@Operation(summary = "목록 조회", description = "" )
	@GetMapping("/config/k8s/list")
	public ResponseWrapper<List<K8SConfig>> getK8SList(@RequestParam(value = "providerCd", required = false) String providerCd) {
		return new ResponseWrapper<>(k8sService.getK8SList(providerCd));
	}

	@Operation(summary = "상세", description = "" )
	@GetMapping("/config/k8s/{k8sId}")
	public ResponseWrapper<K8SConfig> getK8S(@PathVariable int k8sId) {
		return new ResponseWrapper<>(k8sService.getK8S(k8sId));
	}

	@Operation(summary = "등록", description = "" )
	@PostMapping("/config/k8s")
	public ResponseWrapper<Integer> createK8S(@RequestBody K8SConfig k8s) {
		k8s.setRegId("admin");
		k8s.setRegName("admin");
		return new ResponseWrapper<>(k8sService.createK8S(k8s));
	}

	@Operation(summary = "수정", description = "" )
	@PutMapping("/config/k8s/{k8sId}")
	public ResponseWrapper<Integer> updateK8S(@PathVariable int k8sId, @RequestBody K8SConfig k8s) {
		k8s.setK8sId(k8sId);
		k8s.setModId("admin");
		k8s.setModName("admin");
		return new ResponseWrapper<>(k8sService.updateK8S(k8s));
	}

	@Operation(summary = "삭제", description = "" )
	@DeleteMapping("/config/k8s/{k8sId}")
	public ResponseWrapper<Void> deleteK8S(@PathVariable int k8sId) {
		k8sService.deleteK8S(k8sId);
		return new ResponseWrapper<>();
	}

	@Operation(summary = "이름 중복 확인", description = "" )
	@GetMapping("/config/k8s/name/duplicate")
	public ResponseWrapper<Boolean> isNameDuplicate(@RequestParam(value="k8sName") String k8sName) {
		boolean duplicate = k8sService.isNameDuplicate(k8sName);
		return new ResponseWrapper<>(duplicate);
	}

	@Operation(summary = "provider별 count", description = "" )
	@GetMapping("/config/k8s/count")
	public ResponseWrapper<List<Map<String, Object>>> getK8SCount(@RequestParam(value="providerCd", required = false) String prividerCd) {
		return new ResponseWrapper<>(k8sService.getK8SCount(prividerCd));
	}

	@Operation(summary = "연결확인", description = "" )
	@PostMapping("/config/k8s/connection/check")
	public ResponseWrapper<Boolean> checkConnection(@RequestBody ArgocdConfig argocd) {
		if ( StringUtils.isNotBlank(argocd.getArgocdToken()) ) {
			argocd.setArgocdToken(AES256Util.encrypt(Base64Utils.base64Decoding(argocd.getArgocdToken())));
		}
		
		argocdService.getProject(argocd, "M-CMP");
		return new ResponseWrapper<>(Boolean.TRUE);
	}
}
