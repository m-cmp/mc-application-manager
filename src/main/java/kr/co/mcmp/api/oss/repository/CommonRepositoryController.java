package kr.co.mcmp.api.oss.repository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "CommonRepositoryController - Repository API related")
@RequestMapping("/oss/v1/repositories")
@RestController
@RequiredArgsConstructor
public class CommonRepositoryController {

    private final CommonModuleRepositoryService moduleRepositoryService;

    @Operation(summary = "Get repository list")
    @GetMapping("/{module}/list")
    public ResponseEntity<ResponseWrapper<List<CommonRepository.RepositoryDto>>> getRepositoryList(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module) {
        List<CommonRepository.RepositoryDto> repositoryList = moduleRepositoryService.getRepositoryList(module);
        return ResponseEntity.ok(new ResponseWrapper<>(repositoryList));
    }

    @Operation(summary = "Get repository details")
    @GetMapping("/{module}/detail/{name}")
    public ResponseEntity<ResponseWrapper<CommonRepository.RepositoryDto>> getRepositoryDetailByName(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Repository name", required = true) @PathVariable("name") String name) {
        CommonRepository.RepositoryDto repositoryDetailByName = moduleRepositoryService.getRepositoryDetailByName(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>(repositoryDetailByName));
    }

    @Operation(summary = "Register repository")
    @PostMapping("/{module}/create")
    public ResponseEntity<ResponseWrapper<String>> createRepository(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @RequestBody @Valid CommonRepository.RepositoryDto repositoryDto) {
        moduleRepositoryService.createRepository(module, repositoryDto);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository create completed"));
    }

    @Operation(summary = "Update repository")
    @PutMapping("/{module}/update")
    public ResponseEntity<ResponseWrapper<String>> updateRepository(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @RequestBody @Valid CommonRepository.RepositoryDto repositoryDto) {
        moduleRepositoryService.updateRepository(module, repositoryDto);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository update completed"));
    }

    @Operation(summary = "Delete repository")
    @DeleteMapping("/{module}/delete/{name}")
    public ResponseEntity<ResponseWrapper<String>> deleteRepository(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Repository name", required = true) @PathVariable("name") String name) {
        moduleRepositoryService.deleteRepository(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository delete completed"));
    }
}
