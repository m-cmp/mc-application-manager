package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import kr.co.mcmp.externalrepo.model.ArtifactHubRespository;
import kr.co.mcmp.externalrepo.model.DockerHubCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalRepoService {

    @Autowired
    private ArtifactHubInteface artfInt;

    @Autowired
    private DockerHubInterface dockerInt;

    public List<ArtifactHubRespository> searchArtifactHubRepository(String keyword){
        return artfInt.searchRepository(keyword);

    }

    public ArtifactHubPackage searchArtifactHubPackage(String keyword){
        return artfInt.searchPackage(keyword, "0");
    }

    public DockerHubNamespace searchDockerHubNamespace(String keyword){
        return dockerInt.searchNamespace(keyword);
    }

    public DockerHubCatalog searchDockerHubCatalog(String keyword){
        return dockerInt.searchCatalog(keyword);
        //return null;
    }


}
