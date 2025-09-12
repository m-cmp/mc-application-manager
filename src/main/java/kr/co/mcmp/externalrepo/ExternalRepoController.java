package kr.co.mcmp.externalrepo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import kr.co.mcmp.externalrepo.model.DockerHubCatalog;
import kr.co.mcmp.response.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="external repository search", description="External repository search (dockerhub, artifacthub, etc.)")
@RestController
@RequestMapping("/search")
public class ExternalRepoController {

    Logger logger = LoggerFactory.getLogger(ExternalRepoController.class);

    @Autowired
    ExternalRepoService outSvc;


    @Operation(summary = "Get dockerHub catalog (image search)")
    @GetMapping("/dockerhub/{keyword}")
    public ResponseWrapper<DockerHubCatalog> getDockerHubList(@PathVariable String keyword){
        logger.info("testString: {}", keyword);
        if(keyword != null) {
            return new ResponseWrapper<>(outSvc.searchDockerHubCatalog(keyword));
        }else{
            return null;
        }
    }

    @Operation(summary = "Get artifactHub package list (helm search)")
    @GetMapping("/artifacthub/{keyword}")
    public ResponseWrapper<ArtifactHubPackage> getArtifactHubList(@PathVariable String keyword){
        if(keyword != null) {
            return new ResponseWrapper<>(outSvc.searchArtifactHubPackage(keyword));
        }else{
            return null;
        }
    }


}
