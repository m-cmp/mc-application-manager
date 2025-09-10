package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssService;
import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내부 넥서스와 연동하는 서비스 구현체
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class NexusIntegrationServiceImpl implements NexusIntegrationService {
    
    private final CommonModuleRepositoryService moduleRepositoryService;
    private final NexusConfig nexusConfig;
    private final OssService ossService;
    
    /**
     * 소프트웨어 카탈로그를 넥서스에 등록합니다.
     * 
     * @param catalog 등록할 소프트웨어 카탈로그 정보
     * @return 등록 결과 (성공/실패, 메시지, 소스 타입 등)
     */
    @Override
    public Map<String, Object> registerToNexus(SoftwareCatalogDTO catalog) {
        log.info("Registering application to Nexus: {} (Source: {})", catalog.getName(), catalog.getSourceType());
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리
            if (nexusConfig.isHybridMode()) {
                switch (catalog.getSourceType().toUpperCase()) {
                    case "DOCKERHUB":
                        if (nexusConfig.isUseExternalDockerHub()) {
                            result.put("success", true);
                            result.put("message", "Application uses external Docker Hub - no Nexus registration needed");
                            result.put("sourceType", "DOCKERHUB");
                            result.put("imageUrl", "docker.io/" + catalog.getPackageInfo().getPackageName() + ":" + catalog.getPackageInfo().getPackageVersion());
                        } else {
                            // 내부 넥서스에 등록
                            result = registerToInternalNexus(catalog);
                        }
                        break;
                    case "ARTIFACTHUB":
                        if (nexusConfig.isUseExternalArtifactHub()) {
                            result.put("success", true);
                            result.put("message", "Application uses external Artifact Hub - no Nexus registration needed");
                            result.put("sourceType", "ARTIFACTHUB");
                            result.put("chartUrl", catalog.getHelmChart().getChartRepositoryUrl());
                        } else {
                            // 내부 넥서스에 등록
                            result = registerToInternalNexus(catalog);
                        }
                        break;
                    case "NEXUS":
                    default:
                        // 내부 넥서스에 등록
                        result = registerToInternalNexus(catalog);
                        break;
                }
            } else {
                // 하이브리드 모드가 아닌 경우 항상 넥서스에 등록
                result = registerToInternalNexus(catalog);
            }
            
            log.info("Application registration completed: {}", catalog.getName());
            return result;
            
        } catch (Exception e) {
            log.error("Failed to register application: {}", catalog.getName(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to register application: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에 애플리케이션을 등록합니다.
     * 
     * @param catalog 등록할 소프트웨어 카탈로그 정보
     * @return 등록 결과 (성공/실패, 메시지, 레포지토리명, 소스 타입)
     */
    private Map<String, Object> registerToInternalNexus(SoftwareCatalogDTO catalog) {
        try {
            // 1. 먼저 Docker Repository가 존재하는지 확인하고, 없으면 생성
            String repositoryName = catalog.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
            Map<String, Object> repositoryResult = ensureDockerRepositoryExists(repositoryName);
            
            if (!(Boolean) repositoryResult.get("success")) {
                log.error("Failed to ensure Docker repository exists: {}", repositoryName);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to ensure Docker repository exists: " + repositoryResult.get("message"));
                result.put("repositoryName", repositoryName);
                result.put("sourceType", "NEXUS");
                return result;
            }
            
            log.info("Docker repository ensured: {} (action: {})", repositoryName, repositoryResult.get("action"));
            
            // 2. 넥서스 레포지토리 DTO 생성
            CommonRepository.RepositoryDto repositoryDto = createRepositoryDto(catalog);
            
            // 3. 넥서스에 레포지토리 생성 (이미 존재하면 무시됨)
            moduleRepositoryService.createRepository("nexus", repositoryDto);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Application registered to internal Nexus successfully");
            result.put("repositoryName", repositoryName);
            result.put("sourceType", "NEXUS");
            result.put("repositoryAction", repositoryResult.get("action"));
            result.put("repositoryMessage", repositoryResult.get("message"));
            
            log.info("Application registered to internal Nexus successfully: {} (repository action: {})", 
                    catalog.getName(), repositoryResult.get("action"));
            return result;
            
        } catch (Exception e) {
            log.error("Failed to register application to internal Nexus: {}", catalog.getName(), e);
            throw e;
        }
    }
    
    /**
     * 넥서스에서 특정 애플리케이션 정보를 조회합니다.
     * 
     * @param applicationName 조회할 애플리케이션 이름
     * @return 애플리케이션 상세 정보 (RepositoryDto)
     * @throws RuntimeException 넥서스에서 애플리케이션을 찾을 수 없는 경우
     */
    @Override
    public CommonRepository.RepositoryDto getFromNexus(String applicationName) {
        log.debug("Getting application from Nexus: {}", applicationName);
        
        try {
            return moduleRepositoryService.getRepositoryDetailByName("nexus", applicationName);
        } catch (Exception e) {
            log.error("Failed to get application from Nexus: {}", applicationName, e);
            throw new RuntimeException("Failed to get application from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 모든 애플리케이션 목록을 조회합니다.
     * 
     * @return 넥서스에 등록된 모든 애플리케이션 목록
     * @throws RuntimeException 넥서스에서 애플리케이션 목록을 조회할 수 없는 경우
     */
    @Override
    public List<CommonRepository.RepositoryDto> getAllFromNexus() {
        log.debug("Getting all applications from Nexus");
        
        try {
            return moduleRepositoryService.getRepositoryList("nexus");
        } catch (Exception e) {
            log.error("Failed to get all applications from Nexus", e);
            throw new RuntimeException("Failed to get all applications from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 특정 애플리케이션을 삭제합니다.
     * 
     * @param applicationName 삭제할 애플리케이션 이름
     * @return 삭제 결과 (성공/실패, 메시지)
     */
    @Override
    public Map<String, Object> deleteFromNexus(String applicationName) {
        log.info("Deleting application from Nexus: {}", applicationName);
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서는 내부 넥서스에만 등록된 애플리케이션만 삭제
            if (nexusConfig.isHybridMode()) {
                // 외부 소스(Docker Hub, Artifact Hub)는 삭제할 필요 없음
                result.put("success", true);
                result.put("message", "External source applications do not need deletion from Nexus");
                result.put("applicationName", applicationName);
                result.put("note", "Only internal Nexus applications are deleted");
            } else {
                // 하이브리드 모드가 아닌 경우 넥서스에서 삭제
                moduleRepositoryService.deleteRepository("nexus", applicationName);
                
                result.put("success", true);
                result.put("message", "Application deleted from Nexus successfully");
                result.put("applicationName", applicationName);
            }
            
            log.info("Application deletion completed: {}", applicationName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to delete application from Nexus: {}", applicationName, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to delete application from Nexus: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 넥서스에서 Docker 이미지를 풀합니다.
     * 하이브리드 모드에서는 외부 Docker Hub 또는 내부 넥서스에서 풀할 수 있습니다.
     * 
     * @param imageName 풀할 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과 (성공/실패, 메시지, 이미지 URL, 풀 명령어, 소스)
     */
    @Override
    public Map<String, Object> pullImageFromNexus(String imageName, String tag) {
        log.info("Pulling image from Nexus: {}:{}", imageName, tag);
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리
            if (nexusConfig.isHybridMode()) {
                // 외부 소스의 경우 Docker Hub에서 풀
                if (nexusConfig.isUseExternalDockerHub() || nexusConfig.isUseExternalArtifactHub()) {
                    String externalImageUrl = "docker.io/" + imageName + ":" + tag;
                    
                    result.put("success", true);
                    result.put("message", "Image pull command prepared for external Docker Hub");
                    result.put("imageName", imageName);
                    result.put("tag", tag);
                    result.put("fullImageUrl", externalImageUrl);
                    result.put("pullCommand", "docker pull " + externalImageUrl);
                    result.put("source", "Docker Hub");
                    
                    log.info("Image pull command prepared for Docker Hub: {}", externalImageUrl);
                } else {
                    // 내부 넥서스에서 풀
                    result = pullFromInternalNexus(imageName, tag);
                }
            } else {
                // 하이브리드 모드가 아닌 경우 내부 넥서스에서 풀
                result = pullFromInternalNexus(imageName, tag);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image pull: {}:{}", imageName, tag, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to prepare image pull: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에서 Docker 이미지를 풀합니다.
     * 
     * @param imageName 풀할 이미지 이름
     * @param tag 이미지 태그
     * @return 풀 결과 (성공/실패, 메시지, 이미지 URL, 풀 명령어, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> pullFromInternalNexus(String imageName, String tag) {
        try {
            // 넥서스에서 이미지 존재 여부 확인
            String fullImageName = nexusConfig.getDockerImageUrl(imageName, tag);
            
            // 실제 Docker pull 명령어 실행을 위한 정보 반환
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Image pull command prepared for internal Nexus");
            result.put("imageName", imageName);
            result.put("tag", tag);
            result.put("fullImageUrl", fullImageName);
            result.put("pullCommand", "docker pull " + fullImageName);
            result.put("nexusRegistry", nexusConfig.getDockerRegistryUrl());
            result.put("source", "Internal Nexus");
            
            log.info("Image pull command prepared for internal Nexus: {}", fullImageName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image pull from internal Nexus: {}:{}", imageName, tag, e);
            throw e;
        }
    }
    
    /**
     * 넥서스에 Docker 이미지를 푸시합니다.
     * 현재는 항상 내부 넥서스에만 푸시하도록 구현되어 있습니다.
     * 
     * @param imageName 푸시할 이미지 이름
     * @param tag 이미지 태그
     * @param imageData 이미지 데이터 (현재는 사용되지 않음)
     * @return 푸시 결과 (성공/실패, 메시지, 이미지 URL, 푸시 명령어, 넥서스 레지스트리, 인증 정보, 소스)
     */
    @Override
    public Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData) {
        log.info("Pushing image to internal Nexus: {}:{}", imageName, tag);
        
        try {
            // 푸시는 항상 내부 넥서스에만 수행
            Map<String, Object> result = pushToInternalNexus(imageName, tag, imageData);
            
            log.info("Image push command prepared for internal Nexus: {}:{}", imageName, tag);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to prepare image push to internal Nexus: {}:{}", imageName, tag, e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to prepare image push to internal Nexus: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 내부 넥서스에 Docker 이미지를 푸시합니다.
     * 실제 Docker 명령어를 실행하여 이미지를 빌드하고 푸시합니다.
     * 
     * @param imageName 푸시할 이미지 이름
     * @param tag 이미지 태그
     * @param imageData 이미지 데이터 (현재는 사용되지 않음)
     * @return 푸시 결과 (성공/실패, 메시지, 이미지 URL, 푸시 출력, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> pushToInternalNexus(String imageName, String tag, byte[] imageData) {
        try {
            // 넥서스 Docker 레지스트리에 이미지 푸시를 위한 정보 준비
            String fullImageName = nexusConfig.getDockerImageUrl(imageName, tag);
            
            // 실제 Docker 명령어 실행
            Map<String, Object> result = executeDockerPush(imageName, tag, fullImageName);
            
            log.info("Image push executed for internal Nexus: {}", fullImageName);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to push image to internal Nexus: {}:{}", imageName, tag, e);
            throw e;
        }
    }
    
    /**
     * 넥서스 웹 UI API를 통해 이미지 정보를 등록합니다.
     * 원격 서버 환경에서는 Docker 명령어 대신 넥서스 REST API를 사용합니다.
     * 
     * @param imageName 등록할 이미지 이름
     * @param tag 이미지 태그
     * @param fullImageName 전체 이미지 이름 (사용되지 않음)
     * @return 등록 결과 (성공/실패, 메시지, 이미지 URL, 넥서스 레지스트리, 인증 정보, 소스)
     */
    private Map<String, Object> executeDockerPush(String imageName, String tag, String fullImageName) {
            Map<String, Object> result = new HashMap<>();
        
        try {
            // DB에서 실제 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            log.info("Retrieved Nexus info from DB: URL={}, Username={}", 
                    nexusInfo.getOssUrl(), nexusInfo.getOssUsername());
            
            // 동적으로 Docker 레지스트리 포트 조회
            int dockerPort = getDockerRegistryPort();
            String dockerRegistryUrl = nexusInfo.getOssUrl().replace("http://", "").replace(":8081", ":" + dockerPort);
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Docker registry URL: {}, Port: {}", dockerRegistryUrl, dockerPort);
            
            // 동적으로 Docker 레포지토리 이름 조회
            String dockerRepositoryName = getDockerRepositoryName();
            log.info("Docker repository name: {}", dockerRepositoryName);
            
            // 넥서스 형식의 이미지 이름 구성
            String nexusImageName = dockerRegistryUrl + "/" + dockerRepositoryName + "/" + imageName + ":" + tag;
            
            // 넥서스 Repository에 파일 업로드 방식으로 이미지 등록
            try {
                log.info("Uploading image to Nexus repository: {}:{}", imageName, tag);
                
                // 1. 소스 이미지 풀
                log.info("Pulling source image: {}:{}", imageName, tag);
                ProcessBuilder pullProcess = new ProcessBuilder(
                    "docker", "pull", imageName + ":" + tag
                );
                Process pullProc = pullProcess.start();
                int pullExitCode = pullProc.waitFor();
                
                if (pullExitCode != 0) {
                    log.error("Failed to pull source image: {}:{}", imageName, tag);
                    result.put("success", false);
                    result.put("message", "Failed to pull source image: " + imageName + ":" + tag);
                    return result;
                }
                
                // 2. 이미지를 tar 파일로 저장
                String tarFileName = imageName.replace("/", "_") + "_" + tag + ".tar";
                log.info("Saving image to tar file: {}", tarFileName);
                ProcessBuilder saveProcess = new ProcessBuilder(
                    "docker", "save", "-o", tarFileName, imageName + ":" + tag
                );
                Process saveProc = saveProcess.start();
                int saveExitCode = saveProc.waitFor();
                
                if (saveExitCode != 0) {
                    log.error("Failed to save image to tar file: {}", tarFileName);
                    result.put("success", false);
                    result.put("message", "Failed to save image to tar file: " + tarFileName);
                    return result;
                }
                
                // 3. 넥서스에 파일 업로드
                String uploadUrl = nexusInfo.getOssUrl() + "/service/rest/v1/components?repository=" + dockerRepositoryName;
                log.info("Uploading tar file to Nexus: {}", uploadUrl);
                
                ProcessBuilder uploadProcess = new ProcessBuilder(
                    "curl", "-X", "POST", "-s",
                    "-u", username + ":" + password,
                    "-H", "Content-Type: multipart/form-data",
                    "-F", "docker.asset=" + tarFileName,
                    "-F", "docker.asset.filename=" + tarFileName,
                    "-F", "docker.asset.extension=tar",
                    "-F", "docker.asset.contentType=application/x-tar",
                    uploadUrl
                );
                
                Process uploadProc = uploadProcess.start();
                int uploadExitCode = uploadProc.waitFor();
                
                // 4. 임시 tar 파일 삭제
                try {
                    ProcessBuilder deleteProcess = new ProcessBuilder("rm", "-f", tarFileName);
                    deleteProcess.start().waitFor();
                } catch (Exception e) {
                    log.warn("Failed to delete temporary tar file: {}", tarFileName);
                }
                
                if (uploadExitCode == 0) {
                    log.info("Successfully uploaded image to Nexus: {}:{}", imageName, tag);
                    result.put("success", true);
                    result.put("message", "Image successfully uploaded to Nexus repository");
                } else {
                    log.error("Failed to upload image to Nexus: {}:{}", imageName, tag);
                    result.put("success", false);
                    result.put("message", "Failed to upload image to Nexus: " + imageName + ":" + tag);
                    return result;
                }
                
            } catch (Exception e) {
                log.error("Error during image upload to Nexus", e);
                result.put("success", false);
                result.put("message", "Error during image upload: " + e.getMessage());
                return result;
            }
            
            result.put("imageName", imageName);
            result.put("tag", tag);
            result.put("fullImageUrl", nexusImageName);
            result.put("nexusRegistry", dockerRegistryUrl);
            result.put("nexusCredentials", username + ":***");
            result.put("source", "Internal Nexus (Development Server)");
            result.put("repository", dockerRepositoryName);
            result.put("webUrl", nexusInfo.getOssUrl() + "/#browse/browse:" + dockerRepositoryName);
            result.put("note", "이미지 컴포넌트가 넥서스에 등록되었습니다. 실제 이미지 데이터는 Docker 명령어로 업로드하세요.");
            result.put("dockerCommands", createDockerCommands(nexusImageName, username, password, dockerRegistryUrl, imageName, tag));
            
            log.info("Image information registered to Nexus: {}:{} in repository: {}", 
                    imageName, tag, dockerRepositoryName);
            
        } catch (Exception e) {
            log.error("Error registering image information to Nexus", e);
            result.put("success", false);
            result.put("message", "Error registering image information: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Docker 명령어를 생성합니다.
     * 
     * @param nexusImageName 넥서스 이미지 이름
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @param dockerRegistryUrl Docker 레지스트리 URL
     * @param imageName 원본 이미지 이름
     * @param tag 이미지 태그
     * @return Docker 명령어 목록
     */
    private Map<String, String> createDockerCommands(String nexusImageName, String username, String password, 
                                                    String dockerRegistryUrl, String imageName, String tag) {
        Map<String, String> commands = new HashMap<>();
        
        // 소스 이미지 (Docker Hub에서)
        String sourceImage = imageName.contains("/") ? imageName + ":" + tag : imageName + ":latest";
        
        commands.put("pull_source", "docker pull " + sourceImage);
        commands.put("tag_image", "docker tag " + sourceImage + " " + nexusImageName);
        commands.put("login", "docker login " + dockerRegistryUrl + " -u " + username + " -p " + password);
        commands.put("push", "docker push " + nexusImageName);
        
        return commands;
    }
    
    /**
     * DB에서 넥서스 OSS 정보를 가져옵니다.
     * OSS 목록에서 이름이 "NEXUS"인 항목을 찾아 반환합니다.
     * 
     * @return 넥서스 OSS 정보 (URL, 사용자명, 비밀번호)
     * @throws RuntimeException DB에서 NEXUS 정보를 찾을 수 없는 경우
     */
    public OssDto getNexusInfoFromDB() {
        try {
            // DB에서 NEXUS OSS 정보 조회
            List<OssDto> ossList = ossService.getAllOssList();
            
            // NEXUS 이름으로 필터링
            OssDto nexusInfo = ossList.stream()
                    .filter(oss -> "NEXUS".equalsIgnoreCase(oss.getOssName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("DB에서 NEXUS 정보를 찾을 수 없습니다."));
            
            log.info("Nexus info loaded from DB: URL={}, Username={}", 
                    nexusInfo.getOssUrl(), nexusInfo.getOssUsername());
            
            return nexusInfo;
        } catch (Exception e) {
            log.error("Failed to get Nexus info from DB", e);
            throw new RuntimeException("DB에서 넥서스 정보를 가져올 수 없습니다: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 특정 이미지의 태그 목록을 조회합니다.
     * 하이브리드 모드에서는 외부 소스의 경우 기본 태그들을 반환하고,
     * 내부 넥서스의 경우 실제 태그를 조회합니다.
     * 
     * @param imageName 조회할 이미지 이름
     * @return 이미지 태그 목록
     * @throws RuntimeException 넥서스에서 태그를 조회할 수 없는 경우
     */
    @Override
    public List<String> getImageTagsFromNexus(String imageName) {
        log.debug("Getting image tags from Nexus: {}", imageName);
        
        try {
            List<String> tags = new ArrayList<>();
            
            // 하이브리드 모드에서 소스 타입에 따라 처리
            // OSS에서 조회한 넥서스 정보를 바탕으로 실제 Docker 레포지토리에서 태그 조회
            tags = getTagsFromInternalNexus(imageName);
            
            // 실제 넥서스에서 태그를 찾지 못한 경우에만 기본 태그 제공
            if (tags.isEmpty()) {
                log.info("No tags found in Nexus for image '{}', providing default tags", imageName);
                    tags.add("latest");
                    tags.add("stable");
                    tags.add("v1.0.0");
                    tags.add("v1.1.0");
                    tags.add("v2.0.0");
            }
            
            log.debug("Retrieved {} tags for image: {} from Nexus", tags.size(), imageName);
            return tags;
            
        } catch (Exception e) {
            log.error("Failed to get image tags from Nexus: {}", imageName, e);
            throw new RuntimeException("Failed to get image tags from Nexus: " + e.getMessage());
        }
    }
    
    /**
     * 내부 넥서스에서 특정 이미지의 태그 목록을 동적으로 조회합니다.
     * OSS에서 조회한 넥서스 정보를 바탕으로 Docker 레포지토리에서 실제 태그를 조회합니다.
     * 
     * @param imageName 조회할 이미지 이름
     * @return 이미지 태그 목록 (실제 넥서스에서 조회한 태그 또는 빈 목록)
     */
    private List<String> getTagsFromInternalNexus(String imageName) {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            
            // 넥서스 REST API URL 구성
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            // 동적으로 Docker 레포지토리 이름 조회
            String dockerRepositoryName = getDockerRepositoryName();
            
            log.info("Querying tags for image '{}' in repository '{}' from Nexus: {}", 
                    imageName, dockerRepositoryName, nexusUrl);
            
            // 넥서스 검색 API 호출
            String searchUrl = nexusUrl + "/service/rest/v1/search?repository=" + dockerRepositoryName + "&name=" + imageName;
            
            // HTTP 요청을 위한 ProcessBuilder 사용
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, searchUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.error("Failed to query Nexus API (exit code: {}) for image: {}", exitCode, imageName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            String responseStr = response.toString();
            if (responseStr.trim().isEmpty()) {
                log.warn("Empty response from Nexus API for image: {}", imageName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            log.debug("Nexus search response for image '{}': {}", imageName, responseStr);
            
            // JSON 응답 파싱하여 태그 추출
            List<String> tags = parseTagsFromNexusResponse(responseStr, imageName);
            
            if (tags.isEmpty()) {
                log.info("No tags found for image '{}' in Nexus repository '{}'", imageName, dockerRepositoryName);
                return new ArrayList<>(); // 빈 목록 반환
            }
            
            log.info("Successfully found {} tags for image '{}' in repository '{}': {}", 
                    tags.size(), imageName, dockerRepositoryName, tags);
            return tags;
            
        } catch (Exception e) {
            log.error("Failed to get tags from internal Nexus for image: {}", imageName, e);
            return new ArrayList<>(); // 빈 목록 반환
        }
    }
    
    /**
     * 넥서스에 Docker Repository가 있는지 확인하고, 없으면 생성합니다.
     *
     * @param repositoryName Repository 이름
     * @return Repository 생성/확인 결과
     */
    @Override
    public Map<String, Object> ensureDockerRepositoryExists(String repositoryName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Checking if Docker repository exists: {}", repositoryName);
            
            // 1. 먼저 Repository가 존재하는지 확인
            if (isDockerRepositoryExists(repositoryName, nexusUrl, username, password)) {
                log.info("Docker repository already exists: {}", repositoryName);
                result.put("success", true);
                result.put("message", "Docker repository already exists: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "exists");
                return result;
            }
            
            // 2. Repository가 없으면 생성
            log.info("Creating Docker repository: {}", repositoryName);
            boolean created = createDockerRepository(repositoryName, nexusUrl, username, password);
            
            if (created) {
                log.info("Successfully created Docker repository: {}", repositoryName);
                result.put("success", true);
                result.put("message", "Docker repository created successfully: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "created");
            } else {
                log.error("Failed to create Docker repository: {}", repositoryName);
                result.put("success", false);
                result.put("message", "Failed to create Docker repository: " + repositoryName);
                result.put("repositoryName", repositoryName);
                result.put("action", "failed");
            }
            
        } catch (Exception e) {
            log.error("Error ensuring Docker repository exists: {}", repositoryName, e);
            result.put("success", false);
            result.put("message", "Error ensuring Docker repository exists: " + e.getMessage());
            result.put("repositoryName", repositoryName);
            result.put("action", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Docker Repository가 존재하는지 확인합니다.
     *
     * @param repositoryName Repository 이름
     * @param nexusUrl 넥서스 URL
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @return Repository 존재 여부
     */
    private boolean isDockerRepositoryExists(String repositoryName, String nexusUrl, String username, String password) {
        try {
            String repositoriesUrl = nexusUrl + "/service/rest/v1/repositories";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, repositoriesUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.error("Failed to query Nexus repositories (exit code: {})", exitCode);
                return false;
            }
            
            String responseStr = response.toString();
            return responseStr.contains("\"name\":\"" + repositoryName + "\"") && 
                   responseStr.contains("\"format\":\"docker\"");
            
        } catch (Exception e) {
            log.error("Error checking if Docker repository exists: {}", repositoryName, e);
            return false;
        }
    }
    
    /**
     * Docker Repository를 생성합니다.
     *
     * @param repositoryName Repository 이름
     * @param nexusUrl 넥서스 URL
     * @param username 넥서스 사용자명
     * @param password 넥서스 비밀번호
     * @return 생성 성공 여부
     */
    private boolean createDockerRepository(String repositoryName, String nexusUrl, String username, String password) {
        try {
            String createUrl = nexusUrl + "/service/rest/v1/repositories/docker/hosted";
            
            // Docker Repository 생성 JSON
            String repositoryConfig = String.format(
                "{\"name\":\"%s\",\"online\":true,\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true,\"writePolicy\":\"allow_once\"},\"docker\":{\"v1Enabled\":false,\"forceBasicAuth\":true,\"httpPort\":5000}}",
                repositoryName
            );
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-X", "POST", "-s",
                "-u", username + ":" + password,
                "-H", "Content-Type: application/json",
                "-d", repositoryConfig,
                createUrl
            );
            
            Process curlProc = curlProcess.start();
            int exitCode = curlProc.waitFor();
            
            if (exitCode == 0) {
                log.info("Docker repository created successfully: {}", repositoryName);
                return true;
            } else {
                log.error("Failed to create Docker repository (exit code: {})", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error creating Docker repository: {}", repositoryName, e);
            return false;
        }
    }

    /**
     * OSS에서 조회한 넥서스 정보를 바탕으로 Docker 타입의 레포지토리를 동적으로 조회합니다.
     *
     * @return Docker 타입 레포지토리 이름
     * @throws RuntimeException Docker 레포지토리를 찾을 수 없는 경우
     */
    public String getDockerRepositoryName() {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            log.info("Querying Nexus repositories from: {}", nexusUrl);
            
            // 넥서스에서 모든 레포지토리 목록 조회
            String repositoriesUrl = nexusUrl + "/service/rest/v1/repositories";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, repositoriesUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.error("Failed to query Nexus repositories (exit code: {})", exitCode);
                throw new RuntimeException("Failed to query Nexus repositories");
            }
            
            String responseStr = response.toString();
            if (responseStr.trim().isEmpty()) {
                log.error("Empty response from Nexus repositories API");
                throw new RuntimeException("Empty response from Nexus repositories API");
            }
            
            log.debug("Nexus repositories response: {}", responseStr);
            
            // JSON 응답에서 Docker 레포지토리 찾기
            String dockerRepo = parseDockerRepositoryFromResponse(responseStr);
            
            if (dockerRepo != null && !dockerRepo.isEmpty()) {
                log.info("Successfully found Docker repository: {}", dockerRepo);
                return dockerRepo;
            } else {
                log.error("No Docker repository found in Nexus. Available repositories: {}", responseStr);
                throw new RuntimeException("No Docker repository found in Nexus");
            }
            
        } catch (Exception e) {
            log.error("Failed to get Docker repository name from Nexus", e);
            throw new RuntimeException("Failed to get Docker repository name: " + e.getMessage());
        }
    }
    
    /**
     * 넥서스에서 Docker 레지스트리 포트를 동적으로 조회합니다.
     * 
     * @return Docker 레지스트리 포트
     */
    private int getDockerRegistryPort() {
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String nexusUrl = nexusInfo.getOssUrl();
            String username = nexusInfo.getOssUsername();
            String password = Base64Utils.base64Decoding(nexusInfo.getOssPassword());
            
            // 넥서스 상태 정보 조회
            String statusUrl = nexusUrl + "/service/rest/v1/status";
            
            ProcessBuilder curlProcess = new ProcessBuilder(
                "curl", "-s", "-u", username + ":" + password, statusUrl
            );
            
            Process curlProc = curlProcess.start();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(curlProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            int exitCode = curlProc.waitFor();
            if (exitCode != 0) {
                log.warn("Failed to query Nexus status, using default port");
                return 8081;
            }
            
            // JSON 응답에서 Docker 레지스트리 포트 찾기
            int dockerPort = parseDockerPortFromResponse(response.toString());
            
            if (dockerPort > 0) {
                log.info("Found Docker registry port: {}", dockerPort);
                return dockerPort;
            } else {
                log.warn("No Docker registry port found, using default");
                return 8081;
            }
            
        } catch (Exception e) {
            log.error("Failed to get Docker registry port", e);
            return 8081;
        }
    }
    
    /**
     * 넥서스 API 응답에서 Docker 타입의 레포지토리 이름을 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @return Docker 레포지토리 이름 (찾지 못하면 null)
     */
    private String parseDockerRepositoryFromResponse(String jsonResponse) {
        try {
            log.debug("Parsing Docker repository from response: {}", jsonResponse);
            
            // JSON에서 "format":"docker"인 레포지토리 찾기
            // 더 정확한 정규식 패턴 사용
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"format\"\\s*:\\s*\"docker\"[^}]*\\}"
            );
            java.util.regex.Matcher matcher = pattern.matcher(jsonResponse);
            
            if (matcher.find()) {
                String repositoryName = matcher.group(1);
                log.info("Successfully parsed Docker repository: {}", repositoryName);
                return repositoryName;
            }
            
            // 정규식이 실패하면 다른 방법으로 시도
            log.warn("Regex pattern failed, trying alternative parsing method");
            
            // 대안: "format":"docker"가 포함된 라인에서 "name" 찾기
            String[] lines = jsonResponse.split("\\{");
            for (String line : lines) {
                if (line.contains("\"format\"") && line.contains("docker")) {
                    int nameStart = line.indexOf("\"name\":\"");
                    if (nameStart > 0) {
                        int nameEnd = line.indexOf("\"", nameStart + 8);
                        if (nameEnd > nameStart) {
                            String repositoryName = line.substring(nameStart + 8, nameEnd);
                            log.info("Found Docker repository using alternative method: {}", repositoryName);
                            return repositoryName;
                        }
                    }
                }
            }
            
            log.error("No Docker repository found in response");
            return null;
            
        } catch (Exception e) {
            log.error("Failed to parse Docker repository from response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 넥서스 API 응답에서 Docker 레지스트리 포트를 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @return Docker 레지스트리 포트
     */
    private int parseDockerPortFromResponse(String jsonResponse) {
        try {
            // JSON에서 Docker 레지스트리 포트 정보 찾기
            // 실제 구현에서는 넥서스 설정에 따라 다를 수 있음
            // 현재는 기본값 반환
            return 8081;
        } catch (Exception e) {
            log.warn("Failed to parse Docker port from response: {}", e.getMessage());
            return 8081;
        }
    }
    
    /**
     * 넥서스 API 응답에서 태그 목록을 파싱합니다.
     * 
     * @param jsonResponse 넥서스 API JSON 응답
     * @param imageName 이미지 이름
     * @return 파싱된 태그 목록
     */
    private List<String> parseTagsFromNexusResponse(String jsonResponse, String imageName) {
        List<String> tags = new ArrayList<>();
        
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson ObjectMapper 사용 권장)
            // "version" 필드에서 태그 추출
            String[] lines = jsonResponse.split("\n");
            for (String line : lines) {
                if (line.contains("\"version\"")) {
                    // "version":"tag" 형태에서 태그 추출
                    int start = line.indexOf("\"version\":\"") + 11;
                    int end = line.indexOf("\"", start);
                    if (start > 10 && end > start) {
                        String tag = line.substring(start, end);
                        if (!tags.contains(tag)) {
                            tags.add(tag);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Nexus response: {}", e.getMessage());
        }
        
        return tags;
    }
    
    /**
     * 소프트웨어 카탈로그 정보를 기반으로 넥서스 레포지토리 DTO를 생성합니다.
     * Docker 호스팅 레포지토리로 설정하여 생성합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 넥서스 레포지토리 DTO (Docker 호스팅 타입)
     */
    private CommonRepository.RepositoryDto createRepositoryDto(SoftwareCatalogDTO catalog) {
        // Docker 설정
        CommonRepository.RepositoryDto.DockerDto dockerDto = CommonRepository.RepositoryDto.DockerDto.builder()
                .v1Enabled(false)
                .forceBasicAuth(true)
                .httpPort(8080)
                .httpsPort(8443)
                .subdomain("/" + catalog.getName().toLowerCase().replaceAll("\\s+", "-"))
                .build();
        
        // Storage 설정
        CommonRepository.RepositoryDto.StorageDto storageDto = CommonRepository.RepositoryDto.StorageDto.builder()
                .blobStoreName("default")
                .strictContentTypeValidation(true)
                .writePolicy("allow")
                .build();
        
        // 레포지토리 DTO 생성
        return CommonRepository.RepositoryDto.builder()
                .name(catalog.getName())
                .format("docker")
                .type("hosted")
                .url("") // 등록시에는 빈값
                .online(true)
                .storage(storageDto)
                .docker(dockerDto)
                .build();
    }
    
    /**
     * 넥서스에 이미지가 존재하는지 확인합니다.
     * 
     * @param imageName 이미지 이름
     * @param tag 이미지 태그
     * @return 이미지 존재 여부
     */
    @Override
    public boolean checkImageExistsInNexus(String imageName, String tag) {
        log.info("Checking if image exists in Nexus: {}:{}", imageName, tag);
        
        try {
            // DB에서 넥서스 정보 가져오기
            OssDto nexusInfo = getNexusInfoFromDB();
            String dockerRepositoryName = getDockerRepositoryName();
            
            // 넥서스 검색 API 호출
            String searchUrl = nexusInfo.getOssUrl() + "/service/rest/v1/search?repository=" + dockerRepositoryName + "&name=" + imageName;
            log.info("Searching for image in Nexus: {}", searchUrl);
            
            ProcessBuilder searchProcess = new ProcessBuilder(
                "curl", "-s", "-u", 
                nexusInfo.getOssUsername() + ":" + Base64Utils.base64Decoding(nexusInfo.getOssPassword()),
                searchUrl
            );
            
            Process searchProc = searchProcess.start();
            int exitCode = searchProc.waitFor();
            
            if (exitCode == 0) {
                // 응답에서 해당 태그가 있는지 확인
                String response = new String(searchProc.getInputStream().readAllBytes());
                log.info("Nexus search response: {}", response);
                
                // JSON 응답에서 태그 확인
                return response.contains("\"version\":\"" + tag + "\"") || 
                       response.contains("\"name\":\"" + imageName + "\"");
            } else {
                log.warn("Failed to search Nexus for image: {}:{}", imageName, tag);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error checking image existence in Nexus: {}:{}", imageName, tag, e);
            return false;
        }
    }
    
    /**
     * 넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.
     * 
     * @param catalog 소프트웨어 카탈로그 정보
     * @return 등록 결과
     */
    @Override
    public Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog) {
        log.info("Pushing image and registering catalog: {}", catalog.getName());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 이미지 정보 추출
            String imageName = catalog.getPackageInfo().getPackageName();
            String tag = catalog.getPackageInfo().getPackageVersion();
            
            // 2. 이미지가 이미 넥서스에 있는지 확인
            boolean imageExists = checkImageExistsInNexus(imageName, tag);
            
            if (imageExists) {
                log.info("Image already exists in Nexus: {}:{}", imageName, tag);
                result.put("imagePushRequired", false);
                result.put("message", "Image already exists in Nexus, proceeding with catalog registration");
            } else {
                log.info("Image not found in Nexus, pushing image: {}:{}", imageName, tag);
                
                // 3. 이미지 푸시
                Map<String, Object> pushResult = pushImageToNexus(imageName, tag, null);
                
                if (!(Boolean) pushResult.get("success")) {
                    result.put("success", false);
                    result.put("message", "Failed to push image to Nexus: " + pushResult.get("message"));
                    return result;
                }
                
                result.put("imagePushRequired", true);
                result.put("imagePushResult", pushResult);
            }
            
            // 4. 카탈로그 등록
            Map<String, Object> catalogResult = registerToNexus(catalog);
            
            if ((Boolean) catalogResult.get("success")) {
                result.put("success", true);
                result.put("message", "Image and catalog successfully registered to Nexus");
                result.put("catalogResult", catalogResult);
            } else {
                result.put("success", false);
                result.put("message", "Failed to register catalog to Nexus: " + catalogResult.get("message"));
            }
            
        } catch (Exception e) {
            log.error("Error pushing image and registering catalog: {}", catalog.getName(), e);
            result.put("success", false);
            result.put("message", "Error during image push and catalog registration: " + e.getMessage());
        }
        
        return result;
    }
}
