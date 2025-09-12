package kr.co.mcmp.oss.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.oss.service.OssTypeService;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "oss type", description = "JENKINS / GITLAB / TUMBLEBUG / Etc...")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/ossType")
@RestController
public class OssTypeController {

    private final OssTypeService ossTypeService;

    @Operation(summary = "Get OSS type list", description = "Retrieve OSS type list" )
    @GetMapping("/list")
    public ResponseWrapper<List<OssTypeDto>> getOssTypeList() {
        return new ResponseWrapper<>(ossTypeService.getAllOssTypeList());
    }

    @Operation(summary = "Get OSS type list (excluding registered)", description = "Retrieve OSS type list excluding registered OSS" )
    @GetMapping("/filter/list")
    public ResponseWrapper<List<OssTypeDto>> getOssTypeFilteredList() {
        return new ResponseWrapper<>(ossTypeService.getOssTypeFilteredList());
    }

    @Operation(summary = "Register OSS type", description = "Register OSS type")
    @PostMapping
    public ResponseWrapper<Long> registOssType(@RequestBody OssTypeDto ossTypeDto) {
        return new ResponseWrapper<>(ossTypeService.registOssType(ossTypeDto));
    }

    @Operation(summary = "Update OSS type", description = "Update OSS type")
    @PatchMapping("/{ossTypeIdx}")
    public ResponseWrapper<Long> updateOssType(@PathVariable Long ossTypeIdx, @RequestBody OssTypeDto ossTypeDto) {
        if ( ossTypeIdx != 0 || ossTypeDto.getOssTypeIdx() != 0 ) {
            return new ResponseWrapper<>(ossTypeService.updateOssType(ossTypeDto));
        }
        return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "OssTypeIdx");
    }

    @Operation(summary = "Delete OSS type", description = "Delete OSS type")
    @DeleteMapping("/{ossTypeIdx}")
    public ResponseWrapper<Boolean> deleteOssType(@PathVariable Long ossTypeIdx) {
        return new ResponseWrapper<>(ossTypeService.deleteOssType(ossTypeIdx));
    }

    @Operation(summary = "Get OSS type details", description = "Retrieve OSS type details")
    @GetMapping("/{ossTypeIdx}")
    public ResponseWrapper<OssTypeDto> detailOssType(@PathVariable Long ossTypeIdx) {
        return new ResponseWrapper<>(ossTypeService.detailOssType(ossTypeIdx));
    }
}
