package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import kr.co.mcmp.externalrepo.model.ArtifactHubRepository;
import kr.co.mcmp.externalrepo.model.DockerHubCatalog;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ExternalRepoService {

    @Autowired
    private ArtifactHubInteface artfInt;

    @Autowired
    private DockerHubInterface dockerInt;

    public List<ArtifactHubRepository> searchArtifactHubRepository(String keyword){
        return artfInt.searchRepository(keyword);

    }

    public ArtifactHubPackage searchArtifactHubPackage(String keyword){
        ArtifactHubPackage test = artfInt.searchPackage(keyword, "0");
        log.info("ArtifactHubPackage : {}", test.toString());
        return test;
    }

    public DockerHubNamespace searchDockerHubNamespace(String keyword){
        return dockerInt.searchNamespace(keyword);
    }

    public DockerHubCatalog searchDockerHubCatalog(String keyword){
        DockerHubCatalog catalog = dockerInt.searchCatalog(keyword);
        log.info("DockerHubCatalog : {}",catalog.toString());
        return catalog;
        //return null;
    }


}
