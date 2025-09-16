package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import kr.co.mcmp.externalrepo.model.ArtifactHubRepository;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "artifactHubClient", url = "https://artifacthub.io")
public interface ArtifactHubInteface {

    @GetMapping(value="/api/v1/repositories/search")
    //https://artifacthub.io/api/v1/repositories/search?offset=0&limit=5&kind=0&name=argo
    List<ArtifactHubRepository> searchRepository(@RequestParam("name") String helm);

    @GetMapping(value="/api/v1/packages/search")
    ArtifactHubPackage searchPackage(@RequestParam("ts_query_web") String helm, @RequestParam(required=false, value="kind", defaultValue="0") String kind);

}
