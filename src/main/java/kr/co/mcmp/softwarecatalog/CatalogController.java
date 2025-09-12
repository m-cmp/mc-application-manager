package kr.co.mcmp.softwarecatalog;

import java.util.List;

import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.category.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.category.dto.SoftwareCatalogRequestDTO;
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

@Tag(name="software catalog crud", description="software catalog information input, modification, etc.")
@RestController
@Log4j2
@RequestMapping("/catalog/software")
@RequiredArgsConstructor
public class CatalogController {


   private final CatalogService catalogService;

    @Operation(summary = "Create catalog")
    @PostMapping
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> createCatalog(
            @RequestBody SoftwareCatalogDTO catalogDTO,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO createdCatalog = catalogService.createCatalog(catalogDTO, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseWrapper<>(createdCatalog));
    }

    @Operation(summary = "Get catalog")
    @GetMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> getCatalog(@PathVariable Long catalogId) {
        SoftwareCatalogDTO catalog = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(catalog));
    }

    @Operation(summary = "Get all catalogs")
    @GetMapping
    public ResponseEntity<ResponseWrapper<List<SoftwareCatalogDTO>>> getAllCatalogs() {
        List<SoftwareCatalogDTO> catalogs = catalogService.getAllCatalogs();
        return ResponseEntity.ok(new ResponseWrapper<>(catalogs));
    }


    @Operation(summary = "Update catalog")
    @PutMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<SoftwareCatalogDTO>> updateCatalog(
            @PathVariable Long catalogId,
            @RequestBody SoftwareCatalogDTO catalogDTO,
            @RequestParam(required = false) String username) {
        SoftwareCatalogDTO updatedCatalog = catalogService.updateCatalog(catalogId, catalogDTO, username);
        return ResponseEntity.ok(new ResponseWrapper<>(updatedCatalog));
    }

    @Operation(summary = "Delete catalog")
    @DeleteMapping("/{catalogId}")
    public ResponseEntity<ResponseWrapper<Void>> deleteCatalog(@PathVariable Long catalogId) {
        catalogService.deleteCatalog(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(ResponseCode.OK));
    }

    @Operation(summary = "Get all catalogs with Nexus information")
    @GetMapping("/combined")
    public ResponseEntity<ResponseWrapper<List<CombinedCatalogDTO>>> getAllCatalogsWithNexusInfo() {
        List<CombinedCatalogDTO> combinedCatalogs = catalogService.getAllCatalogsWithNexusInfo();
        return ResponseEntity.ok(new ResponseWrapper<>(combinedCatalogs));
    }

    @Operation(summary = "Get specific catalog with Nexus information")
    @GetMapping("/{catalogId}/combined")
    public ResponseEntity<ResponseWrapper<CombinedCatalogDTO>> getCatalogWithNexusInfo(@PathVariable Long catalogId) {
        CombinedCatalogDTO combinedCatalog = catalogService.getCatalogWithNexusInfo(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(combinedCatalog));
    }
}
