package kr.co.strato.outer;

import kr.co.strato.catalog.CatalogController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class OuterController {

    Logger logger = LoggerFactory.getLogger(OuterController.class);

    @GetMapping("/dockerhub/{keyword}")
    public String getDockerHubList(){
        return null;
    }


    @GetMapping("/artifacthub/{keyword}")
    public String getArtifactHubList(){
        return null;
    }






}
