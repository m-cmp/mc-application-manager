package kr.co.mcmp.softwarecatalog.application.controller;

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

@Tag(name = "Application Catalog Management", description = "Application Category and Package information management")
@RestController
@Log4j2
@RequestMapping("/catalog/application")
@RequiredArgsConstructor
public class ApplicationCatalogController {

    private final ApplicationService applicationService;

    @Operation(summary = "Get Application Category from DB", description = "Retrieve Application Category stored in DB during Nexus registration.")
    @PostMapping("/category")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getCatalogCategory(@RequestBody SoftwareCatalogRequestDTO.SearchCatalogListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getCategoriesFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "Get Package information from DB", description = "Retrieve Package information stored in DB during Nexus registration.")
    @PostMapping("/package")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getAllApplications(@RequestBody SoftwareCatalogRequestDTO.SearchPackageListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageInfoFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }

    @Operation(summary = "Get Package versions from DB", description = "Retrieve Package versions stored in DB during Nexus registration.")
    @PostMapping("/package/version")
    public ResponseEntity<ResponseWrapper<List<KeyValueDTO>>> getPackageVersion(@RequestBody SoftwareCatalogRequestDTO.SearchPackageVersionListDTO requestDto) {
        List<KeyValueDTO> dto = applicationService.getPackageVersionFromDB(requestDto);
        return ResponseEntity.ok(new ResponseWrapper<>(dto));
    }
}