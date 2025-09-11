package kr.co.mcmp.softwarecatalog.docker;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.dto.DockerHubImageRegistrationRequest;
import kr.co.mcmp.softwarecatalog.SoftwareCatalogDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Tag(name = "Docker Hub Integration", description = "Docker Hub 이미지 검색, 등록 및 관리")
@RestController
@Log4j2
@RequestMapping("/catalog/docker")
@RequiredArgsConstructor
public class DockerHubController {

    private final DockerHubService dockerHubService;

    @Operation(summary = "Docker Hub에서 이미지 검색", description = "Docker Hub에서 이미지를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> searchDockerHubImages(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = dockerHubService.searchDockerHubImages(query, page, pageSize);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Docker Hub 이미지 상세 정보 조회", description = "Docker Hub에서 특정 이미지의 상세 정보를 조회합니다.")
    @GetMapping("/image/{imageName}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getDockerHubImageDetails(
            @PathVariable String imageName,
            @RequestParam(required = false) String tag) {
        Map<String, Object> result = dockerHubService.getDockerHubImageDetails(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Docker Hub 이미지를 카탈로그에 등록", description = "Docker Hub 이미지를 카탈로그에 등록하고 넥서스에 푸시합니다.")
    @PostMapping("/register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> registerDockerHubImage(
            @RequestBody DockerHubImageRegistrationRequest request,
            @RequestParam(required = false) String username) {
        Map<String, Object> result = dockerHubService.registerDockerHubImage(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "넥서스에 이미지 존재 확인", description = "넥서스에 이미지가 존재하는지 확인합니다.")
    @GetMapping("/nexus/image/exists")
    public ResponseEntity<ResponseWrapper<Boolean>> checkImageExistsInNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        boolean result = dockerHubService.checkImageExistsInNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "넥서스에 이미지 푸시", description = "넥서스에 이미지를 푸시합니다.")
    @PostMapping("/nexus/image/push")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pushImageToNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Map<String, Object> result = dockerHubService.pushImageToNexus(imageName, tag, null);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "넥서스에서 이미지 풀", description = "넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pullImageFromNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Map<String, Object> result = dockerHubService.pullImageFromNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "카탈로그 ID로 이미지 풀", description = "카탈로그 ID를 통해 넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull/{catalogId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pullImageByCatalogId(@PathVariable Long catalogId) {
        Map<String, Object> result = dockerHubService.pullImageByCatalogId(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "넥서스에서 이미지 태그 조회", description = "넥서스에서 특정 이미지의 태그 목록을 조회합니다.")
    @GetMapping("/nexus/image/{imageName}/tags")
    public ResponseEntity<ResponseWrapper<List<String>>> getImageTagsFromNexus(@PathVariable String imageName) {
        List<String> result = dockerHubService.getImageTagsFromNexus(imageName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "이미지 푸시 및 카탈로그 등록", description = "넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.")
    @PostMapping("/nexus/image/push-and-register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pushImageAndRegisterCatalog(
            @RequestBody SoftwareCatalogDTO catalog,
            @RequestParam(required = false) String username) {
        Map<String, Object> result = dockerHubService.pushImageAndRegisterCatalog(catalog, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}