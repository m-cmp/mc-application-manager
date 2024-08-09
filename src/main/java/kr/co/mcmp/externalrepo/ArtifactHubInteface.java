package kr.co.mcmp.externalrepo;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "artifactHubClient", url = "https://artifacthub.io")
public interface ArtifactHubInteface {

//    @GetMapping(value="/api/v1/repositories/search")
//    //https://artifacthub.io/api/v1/repositories/search?offset=0&limit=5&kind=0&name=argo
//    List<ArtifactHubRespository> searchRepository(@RequestParam("name") String helm);
//
//    @GetMapping(value="/api/v1/packages/search")
//    ArtifactHubPackage searchPackage(@RequestParam("ts_query_web") String helm, @RequestParam(required=false, value="kind", defaultValue="0") String kind);


}
