package kr.co.strato.outer;

import kr.co.strato.outer.model.ArtifactHubPackage;
import kr.co.strato.outer.model.ArtifactHubRespository;
import kr.co.strato.outer.model.DockerHubCatalog;

import java.util.List;


public interface OuterInterface {

    List<ArtifactHubRespository> searchArtifactHubRepository(String keyword);

    ArtifactHubPackage searchArtifactHubPackage(String keyword);

    DockerHubNamespace searchDockerHubNamespace(String keyword);

    DockerHubCatalog searchDockerHubCatalog(String keyword);


}
