package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.DockerHubCatalog;
import kr.co.mcmp.externalrepo.model.DockerHubTag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "dockerHubClient", url = "https://hub.docker.com/")
public interface DockerHubInterface {

    @GetMapping(value="/v2/namespaces/{keyword}/repositories")
    DockerHubNamespace searchNamespace(@PathVariable("keyword") String keyword);
    //https://hub.docker.com/v2/namespaces/{keyword}/repositories

    @GetMapping(value="/api/search/v3/catalog/search")
    DockerHubCatalog searchCatalog(@RequestParam("query") String keyword)
                                //   @RequestParam(required=false, value="badges", defaultValue="official") String badges);
    //https://hub.docker.com/api/search/v3/catalog/search?query=ubuntu&badges=official


    @GetMapping(value="/v2/repositories/{namespace}/{repository}/tags")
    DockerHubTag searchTags(@PathVariable("namespace") String namespace, @PathVariable("repository") String repository);

}
