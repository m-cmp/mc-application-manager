package m.cmp.appManager.oss.controller;

import java.util.List;

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
import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.gitlab.service.GitLabService;
//import m.cmp.appManager.jenkins.service.JenkinsService;
import m.cmp.appManager.nexus.service.NexusService;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.oss.service.OssService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "oss", description = "oss 설정 (GitLab, Jenkins, Harbor 등)")
@RestController
public class OssController {

	@Autowired
	private OssService ossService;

	@Autowired
	private GitLabService gitlabService;

//	@Autowired
//	private JenkinsService jenkinsService;

	@Autowired
	private NexusService nexusService;

	@Operation(summary = "목록 조회", description = "" )
	@GetMapping("/config/oss/list")
	public ResponseWrapper<List<Oss>> getOssList(@RequestParam(value="ossCd", required=false) String ossCd) {
		return new ResponseWrapper<>(ossService.getOssList(ossCd));
	}

	@Operation(summary = "상세", description = "" )
	@GetMapping("/config/oss/{ossId}")
	public ResponseWrapper<Oss> getOss(@PathVariable int ossId) {
		return new ResponseWrapper<>(ossService.getOss(ossId));
	}
	
	@Operation(summary = "OSS 정보 중복 체크(oss명, url, username)", description = "true : 중복 / false : 중복 아님")
	@GetMapping("/config/oss/duplicate")
	public ResponseWrapper<Boolean> isOssInfoDuplicated(@RequestParam String ossName, @RequestParam String ossUrl, @RequestParam String ossUsername) {
		if ( StringUtils.isBlank(ossName) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossName"); 
		}
		else if ( StringUtils.isBlank(ossUrl) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossUrl"); 
		}
		else if ( StringUtils.isBlank(ossUsername) ) {
    		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossUsername"); 
		}
		
		Oss oss = new Oss();
		oss.setOssName(ossName);
		oss.setOssUrl(ossUrl);
		oss.setOssUsername(ossUsername);
		
		return new ResponseWrapper<>(ossService.isOssInfoDuplicated(oss));
	}

	@Operation(summary = "등록", description = "" )
	@PostMapping("/config/oss")
	public ResponseWrapper<Integer> createOss(@RequestBody Oss oss) {
		oss.setRegId("admin");
		oss.setRegName("admin");
		
		return new ResponseWrapper<>(ossService.createOss(oss));
	}

	@Operation(summary = "수정", description = "" )
	@PutMapping("/config/oss/{ossId}")
	public ResponseWrapper<Integer> updateOss(@PathVariable int ossId, @RequestBody Oss oss) {
		if ( oss.getOssId() == null || oss.getOssId() == 0 ) {
			oss.setOssId(ossId);
		}
		
		oss.setModId("admin");
		oss.setModName("admin");
		
		return new ResponseWrapper<>(ossService.updateOss(oss));
	}


	@Operation(summary = "삭제", description = "" )
	@DeleteMapping("/config/oss/{ossId}")
	public ResponseWrapper<Void> deleteOss(@PathVariable int ossId) {
		ossService.deleteOss(ossId);
		return new ResponseWrapper<>();
	}

	@Operation(summary = "연결확인", description = "" )
	@PostMapping("/config/oss/connection/check")
	public ResponseWrapper<Boolean> checkConnection(@RequestBody Oss oss) {
		switch(oss.getOssCd()) {
//		case "JENKINS" :
//			if ( StringUtils.isBlank(oss.getOssUrl())
//					|| StringUtils.isBlank(oss.getOssUsername())
//					|| StringUtils.isBlank(oss.getOssPassword()) ) {
//	       		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "접속 정보 누락");
//			}
//
//			// Front에서 Base64Encoding한 데이터를 복호화하여 AES256 암호화 함.
//			oss.setOssPassword(ossService.encryptAesString(oss.getOssPassword()));
//
//			return new ResponseWrapper<>(jenkinsService.isJenkinsConnect(oss));
		case "GITLAB" :
			if ( StringUtils.isBlank(oss.getOssUrl()) 
					|| StringUtils.isBlank(oss.getOssUsername())
					|| StringUtils.isBlank(oss.getOssPassword()) ) {				
	       		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "접속 정보 누락");
			}
			
			// Front에서 Base64Encoding한 데이터를 복호화하여 AES256 암호화 함.
			oss.setOssPassword(ossService.encryptAesString(oss.getOssPassword()));
			
			return new ResponseWrapper<>(gitlabService.isConnectByPw(oss));
		case "NEXUS" :
			if ( StringUtils.isBlank(oss.getOssUrl())
				|| StringUtils.isBlank(oss.getOssUsername()) ) {				
	       		return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "접속 정보 누락");
			}
			
			// Front에서 Base64Encoding한 데이터를 복호화하여 AES256 암호화 함.
			oss.setOssToken(ossService.encryptAesString(oss.getOssToken()));
			
			return new ResponseWrapper<>(nexusService.checkNexusConnection(oss));
		default: 
			log.debug("[checkConnection] oss code >>> {}", oss.getOssCd());
			return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "등록되지 않는 Code입니다.");
		}
	}
}