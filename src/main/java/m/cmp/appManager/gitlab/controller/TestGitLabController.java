package m.cmp.appManager.gitlab.controller;

import org.gitlab4j.api.models.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.gitlab.service.GitLabService;
import m.cmp.appManager.oss.model.Oss;


@RestController
public class TestGitLabController {

	@Autowired
	private GitLabService gitLabService;
	
	@GetMapping("/gitlab/connect")
	public ResponseWrapper<Boolean> getApplication(@RequestParam(value="url") String url, @RequestParam(value="id") String id, @RequestParam(value="password") String password) {
    	Oss oss = new Oss();
    	oss.setOssUrl(url);
    	oss.setOssUsername(id);
    	oss.setOssPassword(password);

		return new ResponseWrapper<>(gitLabService.isConnectByPw(oss));
	}
	
	@GetMapping("/gitlab/{gitLabId}/project")
	public ResponseWrapper<Project> getProject(@PathVariable Integer gitLabId) {
		return new ResponseWrapper<>(gitLabService.getProject(gitLabId, "product-center", "test-boot-gradle"));
	}

	@GetMapping("/gitlab/{gitLabId}/file")
	public ResponseWrapper<String> getFile(@PathVariable Integer gitLabId) {
		return new ResponseWrapper<>(gitLabService.getFilePath(gitLabId, "product-center", "test-boot-gradle", "gradlew", "master"));
	}

}
