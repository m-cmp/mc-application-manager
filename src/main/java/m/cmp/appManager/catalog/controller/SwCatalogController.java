package m.cmp.appManager.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.jenkins.pipeline.model.Pipeline;
import m.cmp.appManager.catalog.model.SwCatalog;
import m.cmp.appManager.catalog.model.SwCatalogDetail;
import m.cmp.appManager.catalog.service.SwCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Software Catalog", description = "sw 카탈로그 관리")
@RequestMapping("/catalog/software")
@RestController
public class SwCatalogController {

    @Autowired
    private SwCatalogService swCatalogSvc;

    @Operation(summary = "카탈로그 목록 조회")
    @GetMapping("/")
    public ResponseWrapper<List<SwCatalog>> getSwCatalog(){
        return new ResponseWrapper<>(swCatalogSvc.getSwCatalogList());
    }

    @Operation(summary = "카탈로그 내용 조회")
    @GetMapping("/{scIdx}")
    public ResponseWrapper<SwCatalog> getAppCtDetail(@PathVariable Integer scIdx) {
        return new ResponseWrapper<>(swCatalogSvc.getSwCatalogDetail(scIdx));
    }

    @Operation(summary = "카탈로그 내용 입력")
    @PostMapping("/")
    public ResponseWrapper<SwCatalog> setAppCt(@RequestBody SwCatalogDetail swCatalogDetail){
        return new ResponseWrapper<>(swCatalogSvc.setSwCatalog(swCatalogDetail));
    }

    @Operation(summary = "카탈로그 내용 삭제")
    @DeleteMapping("/{scIdx}")
    public ResponseWrapper<Boolean> delSwCatalog(@PathVariable Integer scIdx){
        return new ResponseWrapper<>(swCatalogSvc.delSwCatalog(scIdx));
    }

    @Operation(summary = "카탈로그 내용 수정")
    @PutMapping("/")
    public ResponseWrapper<SwCatalog> editSwCatalog(@RequestBody SwCatalogDetail swCatalogDetail){
        return new ResponseWrapper<>(swCatalogSvc.editSwCatalog(swCatalogDetail));
    }


    @Operation(summary = "연관 카탈로그 추가")
    @PostMapping("/relation/sw-catalog/{scIdx}")
    public ResponseWrapper<Boolean> addAppCtRelation(@PathVariable Integer scIdx, @RequestBody SwCatalog swCatalog){
        return new ResponseWrapper<>(swCatalogSvc.addSwCatalogRelation(scIdx, swCatalog.getScIdx()));
    }

    @Operation(summary = "연관 카탈로그 삭제")
    @DeleteMapping("/relation/sw-catalog/{scIdx}")
    public ResponseWrapper<Boolean> delAppCtRelation(@PathVariable Integer scIdx, @RequestBody SwCatalog swCatalog){
        return new ResponseWrapper<>(swCatalogSvc.delSwCatalogRelation(scIdx, swCatalog.getScIdx()));
    }

    @Operation(summary = "연관 workflow 추가")
    @PostMapping("/relation/workflow/{scIdx}")
    public ResponseWrapper<Boolean> addAppCtWorkflow(@PathVariable Integer scIdx, @RequestBody Pipeline pipeline){
        return new ResponseWrapper<>(swCatalogSvc.addSwCatalogWorkflow(scIdx, pipeline.getPipelineId()));
    }

    @Operation(summary = "연관 workflow 삭제")
    @DeleteMapping("/relation/workflow/{scIdx}")
    public ResponseWrapper<Boolean> delAppCtWorkflow(@PathVariable Integer scIdx, @RequestBody Pipeline pipeline){
        return new ResponseWrapper<>(swCatalogSvc.delSwCatalogWorkflow(scIdx, pipeline.getPipelineId()));
    }

}
