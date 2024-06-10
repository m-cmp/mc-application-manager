package m.cmp.appManager.applications.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.applications.model.ArtifactHubPackageResponse;
import m.cmp.appManager.applications.model.ArtifactHubRespositoryResponse;
import m.cmp.appManager.applications.model.DockerHubCatalogResponse;
import m.cmp.appManager.applications.model.DockerHubNamespaceResponse;
import m.cmp.appManager.applications.service.ApplicationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Applications", description = "어플리케이션 관리")
@RequestMapping("/applications")
@RestController
public class ApplicationsController {

    @Autowired
    private ApplicationsService appSvc;


    @Operation(summary = "artifactHub repository 목록 조회")
    @GetMapping("/search/artifacthub/repository/{keyword}")
    public ResponseWrapper<List<ArtifactHubRespositoryResponse>> getRepositoryList(@PathVariable String keyword){
        https://artifacthub.io/api/v1/repositories/search?offset=0&limit=5&kind=0&name=argo
        return new ResponseWrapper<>(appSvc.searchRepository(keyword));
    }

    @Operation(summary = "artifactHub package 목록 조회")
    @GetMapping("/search/artifacthub/package/{keyword}")
    public ResponseWrapper<ArtifactHubPackageResponse> getPackageList(@PathVariable String keyword){
        return new ResponseWrapper<>(appSvc.searchPackage(keyword));
    }

    @Operation(summary = "dockerHub namespace 조회")
    @GetMapping("/search/dockerhub/namespace/{keyword}")
    public ResponseWrapper<DockerHubNamespaceResponse> getNamespaceInfo(@PathVariable String keyword){
        return new ResponseWrapper<>(appSvc.searchNamespace(keyword));
    }

    @Operation(summary = "dockerHub catalog 조회(image 조회)")
    @GetMapping("/search/dockerhub/catalog/{keyword}")
    public ResponseWrapper<DockerHubCatalogResponse> getCatalogList(@PathVariable String keyword){
        return new ResponseWrapper<>(appSvc.searchCatalog(keyword));
    }




    /*
    @Operation(summary = "application 목록 조회")
    @GetMapping("/list")
    public String getApplicationList(){
        return null;
    }

    @Operation(summary = "application 내용 조회")
    @GetMapping("/{applicationIdx}}")
    public String getApplicationDetail(){
        return null;
    }

    @Operation(summary = "application 등록")
    @PostMapping("/app")
    public String setApplication() {
        return null;
    }

    @Operation(summary = "application 수정")
    @PutMapping("/{applicationIdx}}")
    public String editApplication() {
        return null;
    }

    @Operation(summary = "application 삭제")
    @DeleteMapping("/{applicationIdx}}")
    public String delApplication() {
        return null;
    }

    @Operation(summary = "application 설치")
    @GetMapping("/{applicationIdx}/{mcis}/install")
    public String installApplication() {
        return null;
    }

    @Operation(summary = "application 실행")
    @GetMapping("/{applicationIdx}}/{mcis}/run")
    public String runApplication() {
        return null;
    }

    @Operation(summary = "application 중지")
    @GetMapping("/{applicationIdx}}/{mcis}/stop")
    public String stopApplication() {
        return null;
    }

    @Operation(summary = "dockerHub 목록조회 - container")
    @GetMapping("/dockerhub")
    public ResponseWrapper<List<Catalog>> getCatalogListDockerHub(@RequestBody int nexusId) {
        return null;
    }

    @Operation(summary = "artifactHub 목록조회 - Helm")
    @GetMapping("/artifacthub")
    public ResponseWrapper<List<Catalog>> getCatalogListArtifactHub(@RequestBody int nexusId) {
        return null;
    }
*/

}
