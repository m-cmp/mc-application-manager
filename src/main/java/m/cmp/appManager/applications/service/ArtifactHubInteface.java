package m.cmp.appManager.applications.service;

import m.cmp.appManager.applications.model.ArtifactHubPackageResponse;
import m.cmp.appManager.applications.model.ArtifactHubRespositoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "artifactHubClient", url = "https://artifacthub.io")
public interface ArtifactHubInteface {

    @GetMapping(value="/api/v1/repositories/search")
    //https://artifacthub.io/api/v1/repositories/search?offset=0&limit=5&kind=0&name=argo
    List<ArtifactHubRespositoryResponse> searchRepository(@RequestParam("name") String helm);

    @GetMapping(value="/api/v1/packages/search")
    ArtifactHubPackageResponse searchPackage(@RequestParam("ts_query_web") String helm);


}
