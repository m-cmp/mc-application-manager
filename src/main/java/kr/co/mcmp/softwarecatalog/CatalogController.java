package kr.co.mcmp.softwarecatalog;

import java.util.List;

import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.catetory.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.catetory.dto.SoftwareCatalogRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Tag(name="software catalog crud", description="software catalog 정보 입력, 수정 외")
@RestController
@Log4j2
@RequestMapping("/catalog/software")
@RequiredArgsConstructor
public class CatalogController {


   private final CatalogService catalogService;
   private final ApplicationService applicationService;

    @Operation(summary = "카탈로그 생성")
    @PostMapping
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> createCatalog(
            @RequestBody SoftwareCatalogDTO catalogDTO,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO createdCatalog = catalogService.createCatalog(catalogDTO, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseWrapper<>(createdCatalog));
    }

    @Operation(summary = "카탈로그 조회")
    @GetMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> getCatalog(@PathVariable Long catalogId) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(catalog));
    }

    @Operation(summary = "모든 카탈로그 조회")
    @GetMapping
    public ResponseEntity<ResponseWrapper<List<SoftwareCatalogDTO>>> getAllCatalogs() {
        List<SoftwareCatalogDTO> catalogs = catalogService.getAllCatalogs();
        return ResponseEntity.ok(new ResponseWrapper<>(catalogs));
    }


    @Operation(summary = "카탈로그 업데이트")
    @PutMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> updateCatalog(
            @PathVariable Long catalogId,
            @RequestBody SoftwareCatalogDTO catalogDTO,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO updatedCatalog = catalogService.updateCatalog(catalogId, catalogDTO, username);
        return ResponseEntity.ok(new ResponseWrapper<>(updatedCatalog));
    }

    @Operation(summary = "카탈로그 삭제")
    @DeleteMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<Void>> deleteCatalog(@PathVariable Long catalogId) {
        catalogService.deleteCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(ResponseCode.OK));
    }

    @Operation(summary = "Nexus 정보를 포함한 모든 카탈로그 조회")
    @GetMapping("/combined")
    public ResponseEntity<ResponseWrapper<List<CombinedCatalogDTO>>> getAllCatalogsWithNexusInfo() {
        List<CombinedCatalogDTO> combinedCatalogs = catalogService.getAllCatalogsWithNexusInfo();
        return ResponseEntity.ok(new ResponseWrapper<>(combinedCatalogs));
    }

    @Operation(summary = "Nexus 정보를 포함한 특정 카탈로그 조회")
    @GetMapping("/{catalogId}/combined")
    public ResponseEntity<ResponseWrapper<CombinedCatalogDTO>> getCatalogWithNexusInfo(@PathVariable Long catalogId) {
        CombinedCatalogDTO combinedCatalog = catalogService.getCatalogWithNexusInfo(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(combinedCatalog));
    }
    
    // ===== 넥서스 연동 API 엔드포인트 (카탈로그 관리용) =====

    @Operation(summary = "넥서스에 이미지 존재 확인", description = "넥서스에 이미지가 존재하는지 확인합니다.")
    @GetMapping("/nexus/image/exists")
    public ResponseEntity<ResponseWrapper<Boolean>> checkImageExistsInNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        boolean result = catalogService.checkImageExistsInNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에 이미지 푸시", description = "넥서스에 이미지를 푸시합니다.")
    @PostMapping("/nexus/image/push")
    public ResponseEntity<ResponseWrapper<Object>> pushImageToNexus(
            @RequestParam String imageName,
            @RequestParam String tag,
            @RequestBody byte[] imageData) {
        Object result = catalogService.pushImageToNexus(imageName, tag, imageData);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에서 이미지 풀", description = "넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull")
    public ResponseEntity<ResponseWrapper<Object>> pullImageFromNexus(
            @RequestParam String imageName,
            @RequestParam String tag) {
        Object result = catalogService.pullImageFromNexus(imageName, tag);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "카탈로그 ID로 이미지 풀", description = "카탈로그 ID를 통해 넥서스에서 이미지를 풀합니다.")
    @PostMapping("/nexus/image/pull/{catalogId}")
    public ResponseEntity<ResponseWrapper<Object>> pullImageByCatalogId(@PathVariable Long catalogId) {
        Object result = catalogService.pullImageByCatalogId(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "넥서스에서 이미지 태그 조회", description = "넥서스에서 특정 이미지의 태그 목록을 조회합니다.")
    @GetMapping("/nexus/image/{imageName}/tags")
    public ResponseEntity<ResponseWrapper<List<String>>> getImageTagsFromNexus(@PathVariable String imageName) {
        List<String> result = catalogService.getImageTagsFromNexus(imageName);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
    
    @Operation(summary = "이미지 푸시 및 카탈로그 등록", description = "넥서스에 이미지를 푸시하고 카탈로그에 등록합니다.")
    @PostMapping("/nexus/image/push-and-register")
    public ResponseEntity<ResponseWrapper<Object>> pushImageAndRegisterCatalog(
            @RequestBody SoftwareCatalogDTO catalog,
            @RequestParam(required = false) String username) {
        Object result = catalogService.pushImageAndRegisterCatalog(catalog, username);
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }

    // ==== Nexus에 등록된 Application DB 조회====

    @Operation(summary = "DB에 저장된 Application Category 조회", description = "Nexus 등록시 DB에 저장된 Application Category를 조회합니다.")
    @PostMapping("/category")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getCatalogCategory(@RequestBody SoftwareCatalogRequestDTO.SearchCatalogListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getCategoriesFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "", description = "")
    @PostMapping("/package")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getAllApplications(@RequestBody SoftwareCatalogRequestDTO.SearchPackageListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageInfoFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "", description = "")
    @PostMapping("/package/version")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getPackageVersion(@RequestBody SoftwareCatalogRequestDTO.SearchPackageVersionListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageVersionFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }
}
