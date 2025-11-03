package kr.co.mcmp.applicationmanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.service.AppProvEngineService;
import kr.co.mcmp.applicationmanager.dto.ReadyzResponse;
import kr.co.mcmp.oss.dto.OssDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ApplicationManagerController {

    private final AppProvEngineService appProvEngineService;

    private final CbtumblebugRestApi cbtumblebugRestApi;

    @GetMapping("/readyz")
    @Operation(summary="Check Application-Manager is ready", description="")
    public ResponseEntity<ReadyzResponse>  checkReadyz(){
        boolean checkTumblebug = cbtumblebugRestApi.checkTumblebug();
        if(checkTumblebug){
            return ResponseEntity.ok(new ReadyzResponse("application-manager is ready"));
        }else{
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ReadyzResponse("application-manager is not ready"));
        }

    }

}
