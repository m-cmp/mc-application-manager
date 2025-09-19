package kr.co.mcmp.softwarecatalog.docker;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import kr.co.mcmp.externalrepo.ExternalRepoService;
import kr.co.mcmp.externalrepo.model.DockerHubCatalog;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.CatalogService;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DockerHubImageRegistrationRequest;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;
import kr.co.mcmp.softwarecatalog.application.service.DockerHubIntegrationService;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class DockerHubService {

    private final CatalogService catalogService;
    private final PackageInfoRepository packageInfoRepository;
    private final UserRepository userRepository;
    private final NexusIntegrationService nexusIntegrationService;
    private final DockerHubIntegrationService dockerHubIntegrationService;
    private final ExternalRepoService externalRepoService;

    @Transactional
    public Map<String, Object> registerDockerHubImage(DockerHubImageRegistrationRequest request, String username) {
        log.info("Registering Docker Hub image: {}:{}", request.getName(), request.getTag());

        Map<String, Object> result = new HashMap<>();

        if(request.getId() == null || request.getId().isEmpty()) {
            return result;
        } else if(request.getTag() == null || request.getTag().isEmpty()) {
            return result;
        }

        try {
            // 1. Docker Hub에서 이미지, 태그 정보 조회
            Map<String, Object> imageTagInfo = dockerHubIntegrationService.getImageInfo(request.getId(), request.getTag());
            if (!(Boolean) imageTagInfo.get("success")) {
                result.put("success", false);
                result.put("message", "Failed to get image info from Docker Hub: " + imageTagInfo.get("message"));
                return result;
            }
            // 2. Docker Hub 이미지를 Nexus에 푸시
            Map<String, Object> pushResult = dockerHubIntegrationService.pushImageToNexus(request.getName(),
                    request.getTag());

            // 3. Docker Hub 에서 조회된 Application 정보 Entity set
            PackageInfo entity = PackageInfo.builder()
                    .architectures(
                            request.getRatePlans().get(0) != null ?
                                    request.getRatePlans().get(0).getArchitectures()
                                            .stream()
                                            .map(arch -> arch.getName())
                                            .collect(Collectors.joining(",")) // ✅ join으로 문자열 변환
                                    : null
                    )
                    .categories(request.getCategories().get(0) != null ? request.getCategories().get(0).getName() : "")
                    .dockerCreatedAt(request.getCreatedAt().toLocalDateTime())
                    .dockerImageId(null)
                    .dockerPublisher(request.getPublisher().getName())
                    .dockerShortDescription(request.getShortDescription())
                    .dockerUpdatedAt(request.getUpdatedAt().toLocalDateTime())
                    .dockerSource(request.getSource())
                    .isArchived(request.getArchived())
                    .isAutomated(false)
                    .isOfficial(false)
                    .lastPulledAt((LocalDateTime) imageTagInfo.get("tag_last_pulled"))
                    .operatingSystems(
                            request.getRatePlans().get(0) != null ?
                                    request.getRatePlans().get(0).getOperating_systems()
                                            .stream()
                                            .map(os -> os.getName())
                                            .collect(Collectors.joining(",")) // ✅ join으로 문자열 변환
                                    : null
                    )
                    .packageName(request.getName())
                    .packageType(PackageType.valueOf("DOCKER"))
                    .packageVersion(request.getTag())
                    .pullCount(
                            request.getRatePlans().get(0) != null ?
                                    request.getRatePlans().get(0).getRepositories().get(0) != null ? request.getRatePlans().get(0).getRepositories().get(0).getPull_count() : null
                                    : null)
                    .repositoryUrl("https://hub.docker.com/_/" + request.getName() + "/tags")
                    .starCount(request.getStarCount())
                    .build();

            // DB insert
            packageInfoRepository.save(entity);

            result.put("success", true);
            result.put("message", "Docker Hub image registered successfully");
            // result.put("catalog", savedCatalog);
            result.put("nexusPush", pushResult);

        } catch (Exception e) {
            log.error("Failed to register Docker Hub image: {}:{}", request.getName(), request.getTag(), e);
            result.put("success", false);
            result.put("message", "Failed to register Docker Hub image: " + e.getMessage());
        }

        return result;
    }

    private SoftwareCatalogDTO createCatalogFromDockerImage(DockerHubImageRegistrationRequest request,
            Map<String, Object> imageInfo, String username) {
        @SuppressWarnings("unchecked")
        Map<String, Object> imageData = (Map<String, Object>) imageInfo.get("data");

        return SoftwareCatalogDTO.builder()
                .name(request.getName())
                .description((String) imageData.get("description"))
                .version(request.getTag())
                .category("Docker Image")
                .license((String) imageData.get("license"))
                .homepage((String) imageData.get("homepage"))
                .repositoryUrl((String) imageData.get("repository_url"))
                .documentationUrl((String) imageData.get("documentation_url"))
                // sourceType은 SOFTWARE_SOURCE_MAPPING에서 관리
                .packageName(request.getName())
                .build();
    }

    public Map<String, Object> searchDockerHubImages(String query, int page, int pageSize) {
        return dockerHubIntegrationService.searchImages(query, page, pageSize);
    }

    public Map<String, Object> getDockerHubImageDetails(String imageName, String tag) {
        return dockerHubIntegrationService.getImageDetails(imageName, tag);
    }

    public boolean checkImageExistsInNexus(String imageName, String tag) {
        return nexusIntegrationService.checkImageExistsInNexus(imageName, tag);
    }

    public Map<String, Object> pushImageToNexus(String imageName, String tag, byte[] imageData) {
        return nexusIntegrationService.pushImageToNexus(imageName, tag, imageData);
    }

    public Map<String, Object> pullImageFromNexus(String imageName, String tag) {
        return nexusIntegrationService.pullImageFromNexus(imageName, tag);
    }

    public Map<String, Object> pullImageFromNexusWithAuth(String imageName, String tag) {
        log.info("Pulling image from Nexus with authentication: {}:{}", imageName, tag);

        Map<String, Object> result = new HashMap<>();

        try {
            // Nexus에서 이미지 풀
            Map<String, Object> pullResult = nexusIntegrationService.pullImageFromNexus(imageName, tag);

            if ((Boolean) pullResult.get("success")) {
                result.put("success", true);
                result.put("message", "Image pulled successfully from Nexus");
                result.put("imageUrl", pullResult.get("imageUrl"));
                result.put("nexusInfo", pullResult.get("nexusInfo"));
            } else {
                result.put("success", false);
                result.put("message", "Failed to pull image from Nexus: " + pullResult.get("message"));
            }

        } catch (Exception e) {
            log.error("Error pulling image from Nexus: {}:{}", imageName, tag, e);
            result.put("success", false);
            result.put("message", "Error pulling image from Nexus: " + e.getMessage());
        }

        return result;
    }

    public List<String> getImageTagsFromNexus(String imageName) {
        return nexusIntegrationService.getImageTagsFromNexus(imageName);
    }

    public Map<String, Object> pullImageByCatalogId(Long catalogId) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        if (catalog == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Catalog not found with id: " + catalogId);
            return result;
        }

        return pullImageFromNexusWithAuth(catalog.getName(), catalog.getVersion());
    }

    public Map<String, Object> pushImageAndRegisterCatalog(SoftwareCatalogDTO catalog, String username) {
        log.info("Pushing image and registering catalog: {}", catalog.getName());

        Map<String, Object> result = new HashMap<>();

        try {
            // Nexus에 이미지 푸시 및 카탈로그 등록
            Map<String, Object> nexusResult = nexusIntegrationService.pushImageAndRegisterCatalog(catalog);

            result.put("success", true);
            result.put("message", "Image pushed and catalog registered successfully");
            result.put("nexusResult", nexusResult);

        } catch (Exception e) {
            log.error("Failed to push image and register catalog: {}", catalog.getName(), e);
            result.put("success", false);
            result.put("message", "Failed to push image and register catalog: " + e.getMessage());
        }

        return result;
    }
}