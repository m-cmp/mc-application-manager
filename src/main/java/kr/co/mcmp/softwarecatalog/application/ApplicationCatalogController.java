package kr.co.mcmp.softwarecatalog.application;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import kr.co.mcmp.softwarecatalog.category.dto.KeyValueDTO;
import kr.co.mcmp.softwarecatalog.category.dto.SoftwareCatalogRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Tag(name = "Application Catalog Management", description = "Application Category 및 Package 정보 관리")
@RestController
@Log4j2
@RequestMapping("/catalog/application")
@RequiredArgsConstructor
public class ApplicationCatalogController {

    private final ApplicationService applicationService;

    @Operation(summary = "DB에 저장된 Application Category 조회", description = "Nexus 등록시 DB에 저장된 Application Category를 조회합니다.")
    @PostMapping("/category")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getCatalogCategory(@RequestBody SoftwareCatalogRequestDTO.SearchCatalogListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getCategoriesFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "DB에 저장된 Package 정보 조회", description = "Nexus 등록시 DB에 저장된 Package 정보를 조회합니다.")
    @PostMapping("/package")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getAllApplications(@RequestBody SoftwareCatalogRequestDTO.SearchPackageListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageInfoFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "DB에 저장된 Package 버전 조회", description = "Nexus 등록시 DB에 저장된 Package 버전을 조회합니다.")
    @PostMapping("/package/version")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getPackageVersion(@RequestBody SoftwareCatalogRequestDTO.SearchPackageVersionListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageVersionFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }
}