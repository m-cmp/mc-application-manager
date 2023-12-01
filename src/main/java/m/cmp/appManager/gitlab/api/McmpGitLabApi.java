package m.cmp.appManager.gitlab.api;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitPayload;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.GroupParams;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestDiff;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.Note;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.TreeItem;
import org.gitlab4j.api.models.User;
import org.springframework.stereotype.Component;

import m.cmp.appManager.gitlab.exception.McmpGitLabException;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.util.AES256Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class McmpGitLabApi {
	
	public GitLabApi getApi(Oss oss) throws GitLabApiException {
		String plainTextPassword = AES256Util.decrypt(oss.getOssPassword());
		
		GitLabApi gitLabApi = GitLabApi.oauth2Login(oss.getOssUrl(), oss.getOssUsername(), plainTextPassword);
		gitLabApi.enableRequestResponseLogging(Level.FINE);
		return gitLabApi;
	}

    /**
     * Group 조회 
     *
     */
    public Group getGroups(Oss oss, String search) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		Optional<Group> optionalGroup =  gitLabApi.getGroupApi().getOptionalGroup(search);
    		if (optionalGroup.isPresent()) {
    		    return optionalGroup.get();
    		} else {
    			return null;
    		}
    		
    	} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * Group 생성
     */
	public Group createGroup(Oss oss, GroupParams params) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		return gitLabApi.getGroupApi().createGroup(params);
    	} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }
    
    /**
     * Group 삭제
     *
     */
    public void deleteGroup(Oss oss, String name) {
    	
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		gitLabApi.getGroupApi().deleteGroup(name);
    	} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /**
     * Group member 조회 
     */
	public Member getGroupMember(Oss oss, Object groupIdOrPath, Long userId) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		Optional<Member> optionalMember = gitLabApi.getGroupApi().getOptionalMember(groupIdOrPath, userId);
    		if (optionalMember.isPresent()) {
    			return optionalMember.get();
    		} else {
    			return null;
    		}
    	} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * Group member 등록 
     */
	public Member addGroupMember(Oss oss, Object groupIdOrPath, Long userId, AccessLevel accessLevel) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		return gitLabApi.getGroupApi().addMember(groupIdOrPath, userId, accessLevel);
    	} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

	/**
	 * Group member 삭제
	 */
	public void removeGroupMember(Oss oss, Object groupIdOrPath, Long userId) {

		try (GitLabApi gitLabApi = getApi(oss)) {
			gitLabApi.getGroupApi().removeMember(groupIdOrPath, userId);
		} catch (GitLabApiException e) {
			log.warn("[GitLab]{}", oss.getOssUrl());
			log.warn(e.getMessage(), e);
			throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
		}
	}
    
    /**
     * GitLab 프로젝트 생성
     *
     * */
    public Project createProject(Oss oss, Long namespaceId, Project project) {
    	
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		return gitLabApi.getProjectApi().createProject(namespaceId, project);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /*******
     * GitLab 프로젝트 조회
     * @param oss
     * @param groupName
     * @param projectName
     * @return
     */
    public Optional<Project> getProject(Oss oss, String groupName, String projectName) {
    	return getProject(oss, groupName, projectName, false);
    }

    /*******
     * GitLab 프로젝트 조회
     * @param oss
     * @param groupName
     * @param projectName
     * @param includeStatistics
     * @return
     */
    public Optional<Project> getProject(Oss oss, String groupName, String projectName, Boolean includeStatistics) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		Optional<Project> optional = gitLabApi.getProjectApi().getOptionalProject(groupName, projectName, includeStatistics);
   			return optional;

    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab 프로젝트 삭제
     *
     * */
    public void deleteProject(Oss oss, Long projectId) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		gitLabApi.getProjectApi().deleteProject(projectId);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab 프로젝트 멤버 조회
     *
     * */
    public Member getProjectMember(Oss oss, Object projectIdOrPath, Long userId) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		Optional<Member> optionalMember = gitLabApi.getProjectApi().getOptionalMember(projectIdOrPath, userId);
    		if (optionalMember.isPresent()) {
    			return  optionalMember.get();
    		} else {
    			return null;
    		}
    				
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /**
     * GitLab 프로젝트 멤버 추가
     *
     * */
    public Member addProjectMember(Oss oss, Object projectIdOrPath, Long userId, AccessLevel accessLevel) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getProjectApi().addMember(projectIdOrPath, userId, accessLevel);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab 프로젝트 멤버 권한 변경
     *
     * */
    public Member updateProjectMember(Oss oss, Object projectIdOrPath, Long userId, AccessLevel accessLevel) {

    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getProjectApi().updateMember(projectIdOrPath, userId, accessLevel);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }
    
    /**
     * GitLab 프로젝트 멤버 삭제
     *
     * */
    public void removeProjectMember(Oss oss, Object projectIdOrPath, Long userId) {

    	try (GitLabApi gitLabApi = getApi(oss)) {

    		gitLabApi.getProjectApi().removeMember(projectIdOrPath, userId);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab 사용자 생성
     *
     * */
    public User createUser(Oss oss, User user, CharSequence password) {
    	
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getUserApi().createUser(user, password, true);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /**
     * GitLab 사용자 조회 (userName)
     *
     * */
    public Optional<User> getUserByName(Oss oss, String username) {
    	try (GitLabApi gitLabApi = getApi(oss)) {    		
    		return gitLabApi.getUserApi().getOptionalUser(username);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /**
     * GitLab 파일 Commit
     *
     * */
    public Commit createCommit(Oss oss, Long projectId, CommitPayload commitPayload) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getCommitsApi().createCommit(projectId, commitPayload);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    	
    }

    /**
     * GitLab Branch 생성
     *
     * */
    public Branch createProtectedBranch(Oss oss, Long projectId, String branchName, String ref) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		Branch branch = gitLabApi.getRepositoryApi().createBranch(projectId, branchName, ref);
    		gitLabApi.getRepositoryApi().protectBranch(projectId, branchName);
    		return branch;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }


    /**
     * GitLab Branch 생성
     *
     * */
    public Branch createBranch(Oss oss, Long projectId, String branchName, String ref) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getRepositoryApi().createBranch(projectId, branchName, ref);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab Branch 목록 조회
     *
     * */
    public List<Branch> getBranches(Oss oss, Object projectIdOrPath) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		List<Branch> list = gitLabApi.getRepositoryApi().getBranches(projectIdOrPath);
    		return list;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    	
    }

    /**
     * GitLab Branch 삭제
     *
     * */
    public void deleteBranch(Oss oss, Long projectId, String branchName) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		gitLabApi.getRepositoryApi().deleteBranch(projectId, branchName);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    	
    }

    
    /**
     * 파일 목록가져오기
     * 
     */
    public List<TreeItem> getRepositoryTree(Oss oss, Object projectIdOrPath, String filePath, String branchName) {
    	return getRepositoryTree(oss, projectIdOrPath, filePath, branchName, false);
    }

    /**
     * 파일 목록가져오기
     * 
     */
    public List<TreeItem> getRepositoryTree(Oss oss, Object projectIdOrPath, String filePath, String branchName, boolean recursive) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		List<TreeItem> list = gitLabApi.getRepositoryApi().getTree(projectIdOrPath, filePath, branchName, recursive);
//    		for (TreeItem item : list) {
//    			log.info("[GitLab]{}", item.toString());
//    		}
    		return list;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    
    /**
     * 파일가져오기
     * 
     */
    public Optional<RepositoryFile> getRepositoryFile(Oss oss, Object projectIdorPath, String filePath, String branchName) {
    	
    	filePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
    	
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getRepositoryFileApi().getOptionalFile(projectIdorPath, filePath, branchName);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }
    
    
    /**
     * GitLab Repository 아카이브
     *
     * */
    public InputStream getRepositoryArchive(Oss oss, Object projectIdorPath, String sha, String format) { 
    	
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		return gitLabApi.getRepositoryApi().getRepositoryArchive(projectIdorPath, sha, Constants.ArchiveFormat.forValue(format));

    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    

    /**
     * GitLab Repository 파일 생성 후 커밋
     *
     * */
    public RepositoryFile createRepositoryFile(Oss oss, String projectPath, RepositoryFile file, String branchName, String commitMessage) {
    	try (GitLabApi gitLabApi = getApi(oss)) {

    		return gitLabApi.getRepositoryFileApi().createFile(projectPath, file, branchName, commitMessage);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 
    }

    /**
     * GitLab Repository 파일 수정 후 커밋
     *
     * */
    public RepositoryFile updateRepositoryFile(Oss oss, String projectPath, RepositoryFile file, String branchName, String commitMessage) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getRepositoryFileApi().updateFile(projectPath, file, branchName, commitMessage);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }
    

    /**
     * 6개월 이내 & 커밋 목록 last 100
     *
     * */
    public List<Commit> getCommitList(Oss oss, Object projectIdOrPath, String branchName) {
    	try (GitLabApi gitLabApi = getApi(oss)) {

    		Calendar c = Calendar.getInstance();
    		Date until = c.getTime();
    		c.add(Calendar.MONTH, -3);
    		Date since = c.getTime();
    		
    		List<Commit> commitList = gitLabApi.getCommitsApi().getCommits(projectIdOrPath, branchName, since, until);
    		if (commitList != null && commitList.size() > 100) {
    			return commitList.subList(0, 100);
    		}
    		
    		return commitList;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    
    /**
     * 커밋 내용 상세 조회
     *
     * */
    public List<Diff> getCommitDiff(Oss oss, Object projectIdOrPath, String commitId) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		List<Diff> diffList = gitLabApi.getCommitsApi().getDiff(projectIdOrPath, commitId);
    		return diffList;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }
    
    /**
     * 머지 리퀘스트 목록조회
     */
    public List<MergeRequest> getMergeRequests(Oss oss, Object projectIdOrPath, MergeRequestState mergeRequestState) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		List<MergeRequest> mergeRequestList = gitLabApi.getMergeRequestApi().getMergeRequests(projectIdOrPath, null);
    		return mergeRequestList;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    /**
     * 머지 리퀘스트 생성
     */
    public MergeRequest createMergeRequest(Oss oss, Object projectIdOrPath, MergeRequestParams requestParams) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().createMergeRequest(projectIdOrPath, requestParams);
    		return mergeRequest;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    
    /**
     * 머지 리퀘스트 수정
     */
    public MergeRequest updateMergeRequest(Oss oss, Object projectIdOrPath, Long merageRequestIid, MergeRequestParams requestParams) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().updateMergeRequest(projectIdOrPath, merageRequestIid, requestParams);
    		return mergeRequest;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    
    /**
     * 머지 리퀘스트 삭제
     * @return
     */
    public void deleteMergeRequest(Oss oss, Object projectIdOrPath, Long merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		gitLabApi.getMergeRequestApi().deleteMergeRequest(projectIdOrPath, merageRequestIid);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }
    
    /**
     * 머지 리퀘스트 Diff 조회 
     * @return
     */
    public List<MergeRequestDiff> getMergeRequestDiffs(Oss oss, Object projectIdOrPath, Long merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		List<MergeRequestDiff> diffList = gitLabApi.getMergeRequestApi().getMergeRequestDiffs(projectIdOrPath, merageRequestIid);
    		//getMergeRequestChanges
    		return diffList;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    /**
     * 머지 리퀘스트 Diff 조회 (deprecated in GitLab 15.7 and will be removed in API v5)
     * @return
     */
    public MergeRequest getMergeRequestChanges(Oss oss, Object projectIdOrPath, Long merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().getMergeRequestChanges(projectIdOrPath, merageRequestIid);
    		return mergeRequest;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }


    /**
     * 머지 리퀘스트 Commit 조회
     * @return
     */
    public List<Commit> getMergeRequestCommits(Oss oss, Object projectIdOrPath, int merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		//MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().getMergeRequestChanges(projectIdOrPath, merageRequestIid);
    		List<Commit> commitList = gitLabApi.getMergeRequestApi().getCommits(projectIdOrPath, merageRequestIid);
    		return commitList;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    
    /**
     * 머지 리퀘스트 머지 
     * @return
     */
    public MergeRequest mergeMergeRequest(Oss oss, Object projectIdOrPath, Long merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		MergeRequest mergeRequest = gitLabApi.getMergeRequestApi().acceptMergeRequest(projectIdOrPath, merageRequestIid);
    		return mergeRequest;
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }


    /**
     * 머지 리퀘스트 comment 조회
     */
    public List<Note> getMergeRequestNotes(Oss oss, Object projectIdOrPath, Long merageRequestIid) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getNotesApi().getMergeRequestNotes(projectIdOrPath, merageRequestIid);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }

    /**
     * 머지 리퀘스트 comment 생성
     */
    public Note createMergeRequestNote(Oss oss, Object projectIdOrPath, Long merageRequestIid, String body) {
    	try (GitLabApi gitLabApi = getApi(oss)) {
    		
    		return gitLabApi.getNotesApi().createMergeRequestNote(projectIdOrPath, merageRequestIid, body);
    		
    	} catch (GitLabApiException e) {
    		log.warn("[GitLab]{}", oss.getOssUrl());
    		log.warn(e.getMessage(), e);
    		throw new McmpGitLabException(e.getHttpStatus(), e.getReason(), e.getMessage());
        } 

    }
    

}
