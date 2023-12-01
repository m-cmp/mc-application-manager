package m.cmp.appManager.gitlab.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.exception.McmpException;
import m.cmp.appManager.gitlab.api.McmpGitLabApi;
import m.cmp.appManager.gitlab.exception.McmpGitLabException;
import m.cmp.appManager.gitlab.model.GitlabConfig;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.oss.service.OssService;
import m.cmp.appManager.util.AES256Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitLabService {

	public static final String COMMIT_MSG = "by Strato-MCMP";

	@Autowired
	private OssService ossService;

	@Autowired
	private McmpGitLabApi api;

	/*******
	 * gitLab 연결 확인 (user, password 방식)
	 * @param oss
	 * @return
	 */
	public boolean isConnectByPw(Oss oss) {
		Optional<User> optional = api.getUserByName(oss, oss.getOssUsername());
		if (optional.isPresent()) {
			return true;
		}
		return false;
	}
	
	/*******
	 * GitLab 정보 세팅
	 * @param gitlabCloneHttpUrl
	 * @param config
	 * @return
	 */
	public GitlabConfig parsingGitLabURL(String gitlabCloneHttpUrl) {
		GitlabConfig gitlab = new GitlabConfig();
		
		try {
			URL url = new URL(gitlabCloneHttpUrl);
			
			String path = url.getPath().replace("//", "/").replace("//", "/");
			
			gitlab.setUrl(String.format("%s://%s", url.getProtocol(), url.getAuthority()));
			gitlab.setGroupName(path.split("/")[1]);
			gitlab.setProjectName(path.split("/")[2].replace(".git", ""));
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return gitlab;
	}
	
	/*******
	 * GitLab 프로젝트 연결 확인
	 * @param gitlabCloneHttpUrl
	 * @param gitlab
	 * @return
	 */
	public boolean checkConnection(Oss gitlab, String gitlabCloneHttpUrl) {
		boolean connected = false;
		
		GitlabConfig gitlabConfig = parsingGitLabURL(gitlabCloneHttpUrl);

		try {
			Optional<User> gitlabUser = api.getUserByName(gitlab, gitlab.getOssUsername());
			if ( gitlabUser.isPresent() ) {
				Optional<Project> gitlabProject = api.getProject(gitlab, gitlabConfig.getGroupName(), gitlabConfig.getProjectName());
				if ( gitlabProject.isPresent() ) {
					connected = true;
				}
			}
		} catch (McmpGitLabException ex) {
			log.warn("error code >>> {}, message >>> {}", ex.getCode(), ex.getMessag());
			if ( ex.getCode() == HttpStatus.UNAUTHORIZED.value() ) {
				throw new McmpException(ResponseCode.UNAUTHORIZED, ex.getMessag());
			}
			else {
				connected = false;
			}
		}

		return connected;
	}

	/*******
	 * File 생성 또는 수정
	 * @param gitLabId
	 * @param serviceGroupName
	 * @param projectName
	 * @param filePath
	 * @param branchName
	 * @param content
	 */
	public void createOrUpdateFile(int gitLabId, String serviceGroupName, String projectName, String filePath, String branchName, String content) {
		createOrUpdateFile(gitLabId, serviceGroupName, projectName, filePath, branchName, content, COMMIT_MSG);
	}
	
	/*******
	 * File 생성 또는 수정
	 * @param gitLabId
	 * @param groupName
	 * @param projectName
	 * @param filePath
	 * @param branchName
	 * @param content
	 * @param commitMessage
	 */
	public void createOrUpdateFile(int gitLabId, String groupName, String projectName, String filePath, String branchName, String content, String commitMessage) {

		log.info("Git 파일 생성요청 project={} filePath={}", projectName, filePath);
		Oss oss = ossService.getOss(gitLabId);

		Project project = api.getProject(oss, groupName, projectName).get();
		String projectPath = project.getPathWithNamespace();
		Optional<RepositoryFile> optional = api.getRepositoryFile(oss, projectPath, filePath, branchName);

		try {

			if (optional.isPresent()) {
				// 있으면 수정
				RepositoryFile file = optional.get();

				file.setContent(new String(Base64.encodeBase64(content.getBytes("UTF-8"))));
				api.updateRepositoryFile(oss, projectPath, file, branchName, commitMessage);
			} else {
				// 없으면 생성
				RepositoryFile file = generateRepositoryFile(filePath, content);
				api.createRepositoryFile(oss, projectPath, file, branchName, commitMessage);
			}
			
		} catch (UnsupportedEncodingException e) {
    		log.warn("[GitLab]url={} {}", oss.getOssUrl(), projectPath);
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(ResponseCode.CREATE_FAILED_PROJECT_SOURCE, e.getMessage());
		}

		log.info("Git 파일 생성완료 project={} filePath={}", projectName, filePath);
	}

	public String getFilePath(int gitLabId, String groupName, String projectName, String fileName, String branch) {		
		Oss oss = ossService.getOss(gitLabId);
		Project project = api.getProject(oss, groupName, projectName).get();
		Optional<RepositoryFile> optional = api.getRepositoryFile(oss, project.getPathWithNamespace(),  fileName, branch);
		
		if (optional.isPresent()) {
			RepositoryFile file = optional.get();
			return String.format("./%s", file.getFilePath());
		}
		
		return null;
	}
	
	/**
	 * RepositoryFileReq 객체 생성 gitLab 파일 생성, 수정 시 사용
	 *
	 */
	private RepositoryFile generateRepositoryFile(String filePath, String content)
			throws UnsupportedEncodingException {

		filePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
		
		RepositoryFile repositoryFile = new RepositoryFile();
		repositoryFile.setFilePath(filePath);
		repositoryFile.setContent(new String(Base64.encodeBase64(content.getBytes("UTF-8"))));
		repositoryFile.setEncoding(Constants.Encoding.BASE64);

		return repositoryFile;
	}

	/**
	 * 그룹 조회
	 * 
	 */
	public Group getGroup(int gitLabId, String groupName) {
		Oss oss = ossService.getOss(gitLabId);
		return api.getGroups(oss, groupName);
	}	

	/**
	 * 프로젝트 조회
	 * 
	 */
	public Project getProject(int gitLabId, String groupName, String projectName) {
		Oss oss = ossService.getOss(gitLabId);
		return api.getProject(oss, groupName, projectName).get();
	}

	/**
	 * 프로젝트 저장소 사용량 조회
	 * 
	 */
	public Long getProjectRepositorySize(int gitLabId, String groupName, String projectName) {
		Oss oss = ossService.getOss(gitLabId);
		Project project = api.getProject(oss, groupName, projectName, true).get();
		return project.getStatistics().getRepositorySize();
	}

	/**
	 * 프로젝트 URL
	 * 
	 */
	public String getProjectUrl(int gitLabId, String groupName, String projectName) {
		Oss oss = ossService.getOss(gitLabId);
		Project project = api.getProject(oss, groupName, projectName).get();
		//return project.getHttpUrlToRepo();
		return String.format("%s/%s.git", oss.getOssUrl(), project.getPathWithNamespace());				
	}

	/**
	 * 프로젝트 삭제
	 *
	 */
	public void deleteProject(int gitLabId, String groupName, String projectName) {

		log.info("Git 프로젝트 삭제요청 group={} project={}", groupName, projectName);
		
		Oss oss = ossService.getOss(gitLabId);
		Optional<Project> optional = api.getProject(oss, groupName, projectName);
		if (optional.isPresent()) {
			Project project = optional.get();
			api.deleteProject(oss, project.getId());
			log.info("Git 프로젝트 삭제완료 gitProjectPath={}", project.getPathWithNamespace());
		} else {
			log.info("Git 프로젝트 not found. group={} project={}", groupName, projectName);
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	public void push(int gitLabId, String projectPath, File dir) {
		Oss oss = ossService.getOss(gitLabId);
		String repoUrl = String.format("%s/%s", oss.getOssUrl(), projectPath);
		
		Git git = null;		
		try {

			git = open(dir);
			git.add().addFilepattern(".").call();
			
			CommitCommand commitCommand = git.commit();
			commitCommand.setAuthor("strato-mcmp", "strato-mcmp@strato.co.kr");
			commitCommand.setMessage("[DevOps]add template");
			commitCommand.setCommitter("strato-mcmp", "strato-mcmp@strato.co.kr");
			
			commitCommand.call();
			// 인증끄기
			setSSLVerifyOff(git);
			// 리모트주소등록
			setRemoteAddr(git, repoUrl);
			// push
			push(git, oss.getOssUsername(), oss.getOssPassword());
		} catch (GitAPIException e) {
    		log.warn("[GitLab]url={} {}", oss.getOssUrl(), projectPath);
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(ResponseCode.CREATE_FAILED_PROJECT_SOURCE, e.getMessage());
		} catch (Exception e) {
			log.warn("[GitLab]url={} {}", oss.getOssUrl(), projectPath);
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(ResponseCode.CREATE_FAILED_PROJECT_SOURCE, e.getMessage());
		}
	}
	
	private Git open(File dir) throws GitAPIException, IOException {		
        try {
            return Git.open(dir);
        } catch (RepositoryNotFoundException e) {
 	       	InitCommand command = new InitCommand();
	        command.setDirectory(dir);
	        //command.setInitialBranch("main");
	        return command.call();
        }
	}
	
	/**
	 * 인증서 검증 끄기
	 * 
	 */
	private void setSSLVerifyOff(Git git) throws IOException {
		StoredConfig config = git.getRepository().getConfig();
		config.setBoolean("http", null, "sslVerify", false);
		config.save();
	}

	/**
	 * Remote 주소 등록
	 * 
	 */
	private void setRemoteAddr(Git git, String url) throws GitAPIException, URISyntaxException {
		RemoteAddCommand remoteAddCommand = git.remoteAdd();
		remoteAddCommand.setName("origin");
		remoteAddCommand.setUri(new URIish(url));
		remoteAddCommand.call();
	}

	/**
	 * Push
	 * 
	 */
	private void push(Git git, String username, String password) throws GitAPIException {
		String plainTextPassword = AES256Util.decrypt(password);
		
		PushCommand pushCommand = git.push();
		pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, plainTextPassword));
		pushCommand.setForce(true);
		pushCommand.setRemote("origin");
		pushCommand.call();
	}
}
