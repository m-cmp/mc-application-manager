package m.cmp.appManager.applications.service;

import m.cmp.appManager.applications.model.ArtifactHubPackageResponse;
import m.cmp.appManager.applications.model.ArtifactHubRespositoryResponse;
import m.cmp.appManager.applications.model.DockerHubCatalogResponse;
import m.cmp.appManager.applications.model.DockerHubNamespaceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplicationsService {

    @Autowired
    private ArtifactHubInteface atfInt;

    @Autowired
    private DockerHubInterface dohInt;

    public List<ArtifactHubRespositoryResponse> searchRepository(String keyword){ return atfInt.searchRepository(keyword); }

    public ArtifactHubPackageResponse searchPackage(String keyword){
        return atfInt.searchPackage(keyword);
    }

    public DockerHubNamespaceResponse searchNamespace(String keyword){
        return dohInt.searchNamespace(keyword);
    }

    public DockerHubCatalogResponse searchCatalog(String keyword){
        return dohInt.searchCatalog(keyword);
    }

}

