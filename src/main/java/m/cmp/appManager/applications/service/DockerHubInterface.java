package m.cmp.appManager.applications.service;

import m.cmp.appManager.applications.model.DockerHubCatalogResponse;
import m.cmp.appManager.applications.model.DockerHubNamespaceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "dockerHubClient", url = "https://hub.docker.com/")
public interface DockerHubInterface {

    @GetMapping(value="/v2/namespaces/{keyword}/repositories")
    DockerHubNamespaceResponse searchNamespace(@PathVariable("keyword") String keyword);
    //https://hub.docker.com/v2/namespaces/{keyword}/repositories

    @GetMapping(value="/api/search/v3/catalog/search")
    DockerHubCatalogResponse searchCatalog(@RequestParam("query") String keyword);
    //https://hub.docker.com/api/search/v3/catalog/search?query=ubuntu



}
