package kr.co.mcmp.softwarecatalog.docker;

import java.util.HashMap;
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

@Tag(name = "Docker Hub Integration", description = "Docker Hub image search, registration and management")
@RestController
@Log4j2
@RequestMapping("/catalog/docker")
@RequiredArgsConstructor
public class DockerHubController {

    private final DockerHubService dockerHubService;

    @Operation(summary = "Search Docker Hub images", description = "Search images from Docker Hub.")
    @GetMapping("/search")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> searchDockerHubImages(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = dockerHubService.searchDockerHubImages(query, page, pageSize);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get Docker Hub image details", description = "Retrieve detailed information for a specific image from Docker Hub.")
    @GetMapping("/image/{imageName}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getDockerHubImageDetails(
            @PathVariable String imageName,
            @RequestParam(required = false) String tag) {
        Map<String, Object> result = dockerHubService.getDockerHubImageDetails(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Register Docker Hub image to catalog", description = "Register Docker Hub image to catalog and push to Nexus.")
    @PostMapping("/register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> registerDockerHubImage(
            @RequestBody DockerHubImageRegistrationRequest request,
            @RequestParam(required = false) String username) {
        Map<String, Object> result = dockerHubService.registerDockerHubImage(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Check if image exists in Nexus", description = "Check if image exists in Nexus.")
    @GetMapping("/nexus/image/exists")
    public ResponseEntity<ResponseWrapper<Boolean>> checkImageExistsInNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        boolean result = dockerHubService.checkImageExistsInNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Push image to Nexus", description = "Push image to Nexus.")
    @PostMapping("/nexus/image/push")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pushImageToNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Map<String, Object> result = dockerHubService.pushImageToNexus(imageName, tag, null);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Pull image from Nexus", description = "Pull image from Nexus.")
    @PostMapping("/nexus/image/pull")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pullImageFromNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Map<String, Object> result = dockerHubService.pullImageFromNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Pull image by catalog ID", description = "Pull image from Nexus using catalog ID.")
    @PostMapping("/nexus/image/pull/{catalogId}")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pullImageByCatalogId(@PathVariable Long catalogId) {
        // Map<String, Object> result = dockerHubService.pullImageByCatalogId(catalogId);
        Map<String,Object> result = new HashMap<String, Object>();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Get image tags from Nexus", description = "Retrieve tag list for a specific image from Nexus.")
    @GetMapping("/nexus/image/{imageName}/tags")
    public ResponseEntity<ResponseWrapper<List<String>>> getImageTagsFromNexus(@PathVariable String imageName) {
        List<String> result = dockerHubService.getImageTagsFromNexus(imageName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    @Operation(summary = "Push image and register to catalog", description = "Push image to Nexus and register to catalog.")
    @PostMapping("/nexus/image/push-and-register")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> pushImageAndRegisterCatalog(
            @RequestBody SoftwareCatalogDTO catalog,
            @RequestParam(required = false) String username) {
        // Map<String, Object> result = dockerHubService.pushImageAndRegisterCatalog(catalog, username);
        Map<String,Object> result = new HashMap<String, Object>();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}