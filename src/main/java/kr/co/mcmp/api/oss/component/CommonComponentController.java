package kr.co.mcmp.api.oss.component;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.service.oss.component.CommonModuleComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "CommonComponentController - Component API related")
@RequestMapping("/oss/v1/components")
@RestController
@RequiredArgsConstructor
public class CommonComponentController {

    private final CommonModuleComponentService moduleComponentService;

    @Operation(summary = "Get component list")
    @GetMapping("/{module}/list/{name}")
    public ResponseEntity<ResponseWrapper<List<CommonComponent.ComponentDto>>> getComponentList(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Repository name", required = true) @PathVariable("name") String name) {
        List<CommonComponent.ComponentDto> componentList = moduleComponentService.getComponentList(module, name);
        return ResponseEntity.ok(new ResponseWrapper<>(componentList));
    }

    @Operation(summary = "Get component details")
    @GetMapping("/{module}/detail/{id}")
    public ResponseEntity<ResponseWrapper<CommonComponent.ComponentDto>> getComponentDetailByName(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Component identifier", required = true) @PathVariable("id") String id) {
        CommonComponent.ComponentDto componentDetailByName = moduleComponentService.getComponentDetailByName(module, id);
        return ResponseEntity.ok(new ResponseWrapper<>(componentDetailByName));
    }

    @Operation(summary = "Delete component")
    @DeleteMapping("/{module}/delete/{id}")
    public ResponseEntity<ResponseWrapper<String>> deleteComponent(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Component identifier", required = true) @PathVariable("id") String id) {
        moduleComponentService.deleteComponent(module, id);
        return ResponseEntity.ok(new ResponseWrapper<>("Component delete completed"));
    }

    @Operation(summary = "Register component")
    @PostMapping("/{module}/create/{name}")
    public ResponseEntity<ResponseWrapper<String>> createComponent(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Repository name", required = true) @PathVariable("name") String name,
            @RequestPart(value = "directory") String directory,
            @RequestPart(value = "assets", required = false) List<MultipartFile> files) {
        moduleComponentService.createComponent(module, name, directory, files);
        return ResponseEntity.ok(new ResponseWrapper<>("Component create completed"));
    }

    @Operation(summary = "Register component - text")
    @PostMapping("/{module}/create/{name}/text")
    public ResponseEntity<ResponseWrapper<String>> createComponentByText(
            @Parameter(description = "Module type", required = true, example = "nexus") @PathVariable("module") String module,
            @Parameter(description = "Repository name", required = true) @PathVariable("name") String name,
            @RequestBody @Valid CommonUploadComponent.TextComponentDto textComponent) {
        moduleComponentService.createComponentByText(module, name, textComponent);
        return ResponseEntity.ok(new ResponseWrapper<>("Component create completed"));
    }
}
