package kr.co.mcmp.catalog;

import java.io.UncheckedIOException;
import java.util.List;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Tag(name="software catalog crud", description="software catalog 정보 입력, 수정 외")
@RestController
@Log4j2
@RequestMapping("/catalog/software")
public class CatalogController {


    @Autowired
    CatalogService catalogService;

//    @ApiOperation(value="software catalog list(all)", notes="software catalog 리스트 불러오기")
//    @Operation(summary = "get software catalog list")
//    @GetMapping("/")
//    public List<CatalogDTO> getCatalogList(){
//        return catalogService.getCatalogList();
//    }

    @ApiOperation(value="software catalog list(all)", notes="software catalog 리스트 불러오기")
    @Operation(summary = "get software catalog list")
    @GetMapping
    public List<CatalogDTO> getCatalogList(@RequestParam(required = false) String title){
        if(StringUtils.isEmpty(title)){
            return catalogService.getCatalogList();
        }else {
            return catalogService.getCatalogListSearch(title);
        }
    }

    @Operation(summary = "software catalogd detail(and reference)")
    @ApiOperation(value="software catalog detail", notes="software catalog 내용 확인(연결된 정보들까지)")
    @GetMapping("/{catalogIdx}")
    public CatalogDTO getCatalog(@PathVariable Integer catalogIdx){
        return catalogService.getCatalog(catalogIdx);
    }

    @Operation(summary = "create software catalog", description = "Insert a software catalog with an optional icon file.")
    @ApiOperation(value="software catalog insert", notes="software catalog 등록")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) 
    public CatalogDTO createCatalog(
        @RequestPart(value = "catalogDto") CatalogDTO catalogDto, 
        @RequestPart(value ="iconFile", required = false) MultipartFile iconFile)
    {
        return catalogService.createCatalog(catalogDto, iconFile);
    }

    @Operation(summary = "delete software catalog")
    @ApiOperation(value="software catalog delete", notes="software catalog 삭제")
    @DeleteMapping("/{catalogIdx}")
    public boolean deleteCatalog(@PathVariable Integer catalogIdx){
        return catalogService.deleteCatalog(catalogIdx);
    }

    // @Operation(summary = "update software catalog")
    // @ApiOperation(value="software catalog update", notes="software catalog 수정")
    // @PutMapping
    // public boolean updateCatalog(@RequestBody CatalogDTO catalogDto, ){
    //     return catalogService.updateCatalog(catalogDto);
    // }

    @Operation(summary = "update software catalog")
    @ApiOperation(value="software catalog update", notes="software catalog 수정")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public boolean updateCatalog(
            @RequestPart("catalogDto") CatalogDTO catalogDto,
            @RequestPart(value = "iconFile", required = false) MultipartFile iconFile) {
            return catalogService.updateCatalog(catalogDto, iconFile);
    }


}
