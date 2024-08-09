package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.ArtifactHubPackage;
import kr.co.mcmp.externalrepo.model.ArtifactHubRespository;
import kr.co.mcmp.externalrepo.model.DockerHubCatalog;

import java.util.List;


public interface ExternalRepoInterface {

    List<ArtifactHubRespository> searchArtifactHubRepository(String keyword);

    ArtifactHubPackage searchArtifactHubPackage(String keyword);

    DockerHubNamespace searchDockerHubNamespace(String keyword);

    DockerHubCatalog searchDockerHubCatalog(String keyword);


}
