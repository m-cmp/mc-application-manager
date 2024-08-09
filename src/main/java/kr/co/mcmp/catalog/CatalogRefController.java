package kr.co.mcmp.catalog;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="software catalog reference crud", description="software catalog 관련정보(workflow, homepage, 기타자료) 입력, 수정 외")
@RestController
@RequestMapping("/catalog/software/ref")
public class CatalogRefController {

    Logger logger = LoggerFactory.getLogger(CatalogRefController.class);

    @Autowired
    CatalogRefService catalogRefService;

//    @ApiOperation(value="catalog ref workflow", notes="create workflows reference catalog")
//    @Operation(summary = "create catalog reference workflow")
//    @PostMapping("/workflow")
//    public String createCatalogRefWorkflow(){
//        return null;
//    }

    @Operation(summary = "execute catalog reference workflow")
    @PostMapping("/workflow")
    public String execWorkflow(){
        return null;
    }

    @Operation(summary = "delete catalog reference workflow")
    @DeleteMapping("/{catalogIdx}/{catalogRefIdx}")
    public boolean deleteCatalogRefWorkflow(@PathVariable Integer catalogIdx, @PathVariable Integer catalogRefIdx){
        return false;
    }

    @Operation(summary = "get catalog reference")
    @GetMapping("/{catalogIdx}")
    public List<CatalogRefDTO> getCatalogReference(@PathVariable Integer catalogIdx){
        return null;
    }

    @Operation(summary = "insert software catalog reference(webpage, workflow, etc...)")
    @ApiOperation(value="software catalog ref insert", notes="software catalog 관련정보 등록(webpage, workflow 등)")
    @PostMapping("/{catalogIdx}")
    public CatalogRefDTO createCatalogRef(CatalogRefDTO crDto){
        return catalogRefService.createCatalogRef(crDto);
    }



}
