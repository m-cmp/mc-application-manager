package kr.co.mcmp.api.oss.component;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.service.oss.component.CommonModuleComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "CommonComponentController - 컴포넌트 API 관련")
@RequestMapping("/oss/v1/components")
@RestController
@RequiredArgsConstructor
public class CommonComponentController {

    private final CommonModuleComponentService moduleComponentService;

    @Operation(summary = "컴포넌트 목록 조회")
    @GetMapping("/{module}/list/{name}")
    public ResponseEntity<ResponseWrapper<List<CommonComponent.ComponentDto>>> getComponentList(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "레포지토리 이름", required = true) @PathVariable("name") String name) {
        List<CommonComponent.ComponentDto> componentList = moduleComponentService.getComponentList(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>(componentList));
    }

    @Operation(summary = "컴포넌트 상세 조회")
    @GetMapping("/{module}/detail/{id}")
    public ResponseEntity<ResponseWrapper<CommonComponent.ComponentDto>> getComponentDetailByName(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "컴포넌트 식별자", required = true) @PathVariable("id") String id) {
        CommonComponent.ComponentDto componentDetailByName = moduleComponentService.getComponentDetailByName(module, id);
        return ResponseEntity.ok(new ResponseWrapper<>(componentDetailByName));
    }

    @Operation(summary = "컴포넌트 삭제")
    @DeleteMapping("/{module}/delete/{id}")
    public ResponseEntity<ResponseWrapper<String>> deleteComponent(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "컴포넌트 식별자", required = true) @PathVariable("id") String id) {
        moduleComponentService.deleteComponent(module, id);
        return ResponseEntity.ok(new ResponseWrapper<>("Component delete completed"));
    }

    @Operation(summary = "컴포넌트 등록")
    @PostMapping("/{module}/create/{name}")
    public ResponseEntity<ResponseWrapper<String>> createComponent(
            @Parameter(description = "모듈 타입", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "레포지토리 이름", required = true) @PathVariable("name") String name,
            @RequestPart(value = "directory", required = false) String directory,
            @RequestPart(value = "asset", required = false) List<MultipartFile> files
    ) {
        moduleComponentService.createComponent(module, name, directory, files);
        return ResponseEntity.ok(new ResponseWrapper<>("Component create completed"));
    }
}
