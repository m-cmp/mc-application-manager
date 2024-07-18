package kr.co.strato.catalog;

import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalog/software/ref")
public class CatalogRefController {

    Logger logger = LoggerFactory.getLogger(CatalogRefController.class);

    @ApiOperation(value="catalog ref workflow", notes="create workflows reference catalog")
    @PutMapping("/workflow/{catalogIdx}/{workflowIdx}")
    public String createCatalogRefWorkflow(@PathVariable Integer catalogIdx, @PathVariable Integer workflowIdx){
        return null;
    }

    @PostMapping("/workflow/")
    public String execWorkflow(){
        return null;
    }

    @DeleteMapping("/workflow/{catalogIdx}/{workflowIdx}")
    public String deleteCatalogRefWorkflow(@PathVariable Integer catalogIdx, @PathVariable Integer workflowIdx){
        return null;
    }

    @GetMapping("/workflow/{catalogIdx}")
    public String getCatalogRefWorkflow(@PathVariable Integer catalogIdx){
        return null;
    }



    @ApiOperation(value="catalog ref catalog", notes="create catalog reference catalog")
    @PutMapping("/catalog/{catalogIdx}/{workflowIdx}")
    public String createCatalogRefCatalog(@PathVariable Integer catalogIdx, @PathVariable Integer workflowIdx){
        return null;
    }

    @DeleteMapping("/catalog/{catalogIdx}/{workflowIdx}")
    public String deleteCatalogRefCatalog(@PathVariable Integer catalogIdx, @PathVariable Integer workflowIdx){
        return null;
    }

    @GetMapping("/catalog/{catalogIdx}")
    public String getCatalogRefCatalog(){
        return null;
    }




}
