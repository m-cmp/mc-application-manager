package kr.co.strato.outer;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.strato.api.response.ResponseWrapper;
import kr.co.strato.catalog.CatalogController;
import kr.co.strato.outer.model.ArtifactHubPackage;
import kr.co.strato.outer.model.DockerHubCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class OuterController {

    Logger logger = LoggerFactory.getLogger(OuterController.class);

//    @Autowired
//    OuterInterface outSvc;


    @Operation(summary = "dockerHub catalog 조회(image 조회)")
    @GetMapping("/dockerhub/{keyword}")
    public ResponseWrapper<DockerHubCatalog> getDockerHubList(@PathVariable String keyword){
        logger.info("testString: {}", keyword);
//        if(keyword != null) {
//            return new ResponseWrapper<>(outSvc.searchDockerHubCatalog(keyword));
//        }else{
//            return null;
//        }
        return null;
    }

    @Operation(summary = "artifactHub package 목록 조회(helm 조회)")
    @GetMapping("/artifacthub/{keyword}")
    public ResponseWrapper<ArtifactHubPackage> getArtifactHubList(@PathVariable String keyword){
//        if(keyword != null) {
//            return new ResponseWrapper<>(outSvc.searchArtifactHubPackage(keyword));
//        }else{
//            return null;
//        }
        return null;
    }


}
