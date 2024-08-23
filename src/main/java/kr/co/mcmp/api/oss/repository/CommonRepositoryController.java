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

@Tag(name = "CommonRepositoryController - 레포지토리 API 관련")
@RequestMapping("/oss/v1/repositories")
@RestController
@RequiredArgsConstructor
public class CommonRepositoryController {

    private final CommonModuleRepositoryService moduleRepositoryService;

    @Operation(summary = "레포지토리 목록 조회")
    @GetMapping("/{module}/list")
    public ResponseEntity<ResponseWrapper<List<CommonRepository.RepositoryDto>>> getRepositoryList(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module) {
        List<CommonRepository.RepositoryDto> repositoryList = moduleRepositoryService.getRepositoryList(module);
        return ResponseEntity.ok(new ResponseWrapper<>(repositoryList));
    }

    @Operation(summary = "레포지토리 상세 조회")
    @GetMapping("/{module}/detail/{name}")
    public ResponseEntity<ResponseWrapper<CommonRepository.RepositoryDto>> getRepositoryDetailByName(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "레포지토리 이름", required = true) @PathVariable("name") String name) {
        CommonRepository.RepositoryDto repositoryDetailByName = moduleRepositoryService.getRepositoryDetailByName(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>(repositoryDetailByName));
    }

    @Operation(summary = "레포지토리 등록")
    @PostMapping("/{module}/create")
    public ResponseEntity<ResponseWrapper<String>> createRepository(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @RequestBody @Valid CommonRepository.RepositoryDto repositoryDto) {
        moduleRepositoryService.createRepository(module, repositoryDto);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository create completed"));
    }

    @Operation(summary = "레포지토리 수정")
    @PutMapping("/{module}/update")
    public ResponseEntity<ResponseWrapper<String>> updateRepository(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @RequestBody @Valid CommonRepository.RepositoryDto repositoryDto) {
        moduleRepositoryService.updateRepository(module, repositoryDto);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository update completed"));
    }

    @Operation(summary = "레포지토리 삭제")
    @DeleteMapping("/{module}/delete/{name}")
    public ResponseEntity<ResponseWrapper<String>> deleteRepository(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "레포지토리 이름", required = true) @PathVariable("name") String name) {
        moduleRepositoryService.deleteRepository(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>("Repository delete completed"));
    }
}
