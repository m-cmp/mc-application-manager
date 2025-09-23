package kr.co.mcmp.externalrepo;

import kr.co.mcmp.externalrepo.model.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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

    public DockerHubNamespace searchDockerHubNamespace(String keyword){
        return dockerInt.searchNamespace(keyword);
    }

    public DockerHubCatalog searchDockerHubCatalog(String keyword){
        // 공식 패키지만 검색 (badges=official)
        DockerHubCatalog catalog = dockerInt.searchCatalog(keyword, "official");
        log.info("DockerHubCatalog (Official only): {}",catalog.toString());
        return catalog;
        //return null;
    }

    public List<DockerHubTag.TagResult> searchDockerHubTag(String namespace, String repository){
        DockerHubTag tag = dockerInt.searchTags(namespace, repository);
        List<DockerHubTag.TagResult> tagList = null;
        if(tag != null){
            tagList = tag.getResults();
            log.info("DockerHubTag : {}",tagList.toString());
        }
        return tagList;
    }

    public ArtifactHubPackage searchArtifactHubPackage(String keyword){
        // 공식 패키지만 검색 (official=true)
        ArtifactHubPackage test = artfInt.searchPackage(keyword, "0", "true");
        log.info("ArtifactHubPackage (Official only): {}", test.toString());
        return test;
    }

    public List<ArtifactHubTag.ArtifactHubVersion> searchArtifactHubTag(String packageKind, String repository, String packageName){
        ArtifactHubTag artifactHubInfo = artfInt.searchTags(packageKind, repository, packageName);
        List<ArtifactHubTag.ArtifactHubVersion> tagList = null;
        if(artifactHubInfo != null){
            tagList = artifactHubInfo.getAvailableVersions();
            log.info("ArtifactHubTag : {}",tagList.toString());
        }
        return tagList;
    }

}
