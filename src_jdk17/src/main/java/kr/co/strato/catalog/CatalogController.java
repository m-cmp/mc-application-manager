package kr.co.strato.catalog;

import kr.co.strato.repository.RepositoryController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalog/software")
public class CatalogController {

    Logger logger = LoggerFactory.getLogger(CatalogController.class);


    @GetMapping("/")
    public String getCatalogList(){
        return null;
    }

    @GetMapping("/{catalogIdx}")
    public String getCatalogDetail(@PathVariable String catalogIdx){
        return null;
    }

    @PostMapping("/")
    public String createCatalog(){
        return null;
    }

    @DeleteMapping("/")
    public String deleteCatalog(){
        return null;
    }

    @PutMapping("/")
    public String updateCatalog(){
        return null;
    }



}
