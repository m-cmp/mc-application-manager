package kr.co.mcmp.catalog;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="software catalog crud", description="software catalog 정보 입력, 수정 외")
@RestController
@RequestMapping("/catalog/software")
public class CatalogController {

    Logger logger = LoggerFactory.getLogger(CatalogController.class);

    @Autowired
    CatalogService catalogService;

    @ApiOperation(value="software catalog list(all)", notes="software catalog 리스트 불러오기")
    @Operation(summary = "get software catalog list")
    @GetMapping("/")
    public List<CatalogDTO> getCatalogList(){
        return catalogService.getCatalogList();
    }

//    @ApiOperation(value="software catalog list(all)", notes="software catalog 리스트 불러오기")
//    @Operation(summary = "get software catalog list")
//    @GetMapping("/")
//    public List<CatalogDTO> getCatalogList(@RequestParam String title){
//        return catalogService.getCatalogList(title);
//    }

//    @Operation(summary = "search software catalog")
//    @ApiOperation(value="software catalog list(keyword search)", notes="software catalog 검색")
//    @GetMapping("/list/{keyword}")
//    public List<CatalogDTO> getCatalogList(@PathVariable(required = false) String keyword){
//        return catalogService.getCatalogListSearch(keyword);
//    }

    @Operation(summary = "software catalogd detail(and reference)")
    @ApiOperation(value="software catalog detail", notes="software catalog 내용 확인(연결된 정보들까지)")
    @GetMapping("/{catalogIdx}")
    public CatalogDTO getCatalogDetail(@PathVariable Integer catalogIdx){
        return catalogService.getCatalogDetail(catalogIdx);
    }

    @Operation(summary = "create software catalog")
    @ApiOperation(value="software catalog insert", notes="software catalog 등록")
    @PostMapping("/")
    public CatalogDTO createCatalog(CatalogDTO catalogDto){
        System.out.println("==================================" + catalogDto.getCatalogTitle());
        return catalogService.createCatalog(catalogDto);
    }

    @Operation(summary = "delete software catalog")
    @ApiOperation(value="software catalog delete", notes="software catalog 삭제")
    @DeleteMapping("/{catalogIdx}")
    public boolean deleteCatalog(@PathVariable Integer catalogIdx){
        return catalogService.deleteCatalog(catalogIdx);
    }

    @Operation(summary = "update software catalog")
    @ApiOperation(value="software catalog update", notes="software catalog 수정")
    @PutMapping("/")
    public CatalogDTO updateCatalog(CatalogDTO catalogDto){
        return catalogService.updateCatalog(catalogDto);
    }


}
