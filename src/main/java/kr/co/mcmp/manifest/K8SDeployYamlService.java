package kr.co.mcmp.manifest;

//import kr.co.mcmp.devops.service.RepositoryService;
//import org.gitlab.api.models.GitlabProject;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.io.IOException;

//@Service
public class K8SDeployYamlService {

    /*

    private static Logger log = LoggerFactory.getLogger(K8SDeployYamlService.class);


    @Autowired
    private RepositoryService gitlabService;

    public boolean createK8SYaml(String groupName, String projectName, String projectPath, String deployName, String branch, String yamlContents) throws IOException {
        GitlabProject project = gitlabService.getProject(groupName, projectName);
        String path = "";
        if(projectPath != null) {
            path = convertPath(projectPath);
        }
        String pathYaml = String.format("%sdevops/k8s/%s/deploy.yaml", path, deployName);
        if(project != null){
            boolean existYaml = gitlabService.isExistRespositoryFile(project, pathYaml, branch);
            if(!existYaml){
                return gitlabService.createRepositoryFile(project, pathYaml, yamlContents, branch, RepositoryService.DEFAULT_COMMIT_MESSAGE);
            }else {
                log.info("[K8SBuildDeploy-Creation] deploy.yaml 파일이 이미 존재 합니다. 파일을 update 합니다. project name : {}",project.getName());
                return gitlabService.createRepositoryFile(project, pathYaml, yamlContents, branch, RepositoryService.DEFAULT_COMMIT_MESSAGE);
            }
        }
        return false;
    }


    private String convertPath(String path) {
        if(path.equals(".")) {
            return "";
        }
        if(path.startsWith("/")) {
            path = path.substring(1, path.length());
        }
        if(!path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }
*/
}
