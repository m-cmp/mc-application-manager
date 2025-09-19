package kr.co.mcmp.softwarecatalog.application.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;
import kr.co.mcmp.softwarecatalog.application.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영 히스토리 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/applications/operation-history")
@RequiredArgsConstructor
@Slf4j
public class OperationHistoryController {

    private final ApplicationService applicationService;

    @Operation(summary = "Get all operation history", description = "Retrieve all operation history.")
    @GetMapping("/all")
    public ResponseEntity<ResponseWrapper<List<OperationHistory>>> getAllOperationHistory() {
        List<OperationHistory> result = applicationService.getAllOperationHistory();
        return ResponseEntity.ok(new ResponseWrapper<>(result));
    }
}
