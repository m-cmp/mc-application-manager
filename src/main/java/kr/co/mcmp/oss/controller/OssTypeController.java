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

@Tag(name = "oss 타입", description = "JENKINS / GITLAB / TUMBLEBUG / Etc...")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/ossType")
@RestController
public class OssTypeController {

    private final OssTypeService ossTypeService;

    @Operation(summary = "OSS 타입 목록 조회", description = "oss Type 목록조회" )
    @GetMapping("/list")
    public ResponseWrapper<List<OssTypeDto>> getOssTypeList() {
        return new ResponseWrapper<>(ossTypeService.getAllOssTypeList());
    }

    @Operation(summary = "OSS 타입 목록 조회", description = "등록된 OSS 를 제외한 oss Type 목록조회" )
    @GetMapping("/filter/list")
    public ResponseWrapper<List<OssTypeDto>> getOssTypeFilteredList() {
        return new ResponseWrapper<>(ossTypeService.getOssTypeFilteredList());
    }

    @Operation(summary = "OSS 타입 등록", description = "oss Type 등록")
    @PostMapping
    public ResponseWrapper<Long> registOssType(@RequestBody OssTypeDto ossTypeDto) {
        return new ResponseWrapper<>(ossTypeService.registOssType(ossTypeDto));
    }

    @Operation(summary = "OSS 타입 수정", description = "oss Type 수정")
    @PatchMapping("/{ossTypeIdx}")
    public ResponseWrapper<Long> updateOssType(@PathVariable Long ossTypeIdx, @RequestBody OssTypeDto ossTypeDto) {
        if ( ossTypeIdx != 0 || ossTypeDto.getOssTypeIdx() != 0 ) {
            return new ResponseWrapper<>(ossTypeService.updateOssType(ossTypeDto));
        }
        return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "OssTypeIdx");
    }

    @Operation(summary = "OSS 타입 삭제", description = "oss Type 삭제")
    @DeleteMapping("/{ossTypeIdx}")
    public ResponseWrapper<Boolean> deleteOssType(@PathVariable Long ossTypeIdx) {
        return new ResponseWrapper<>(ossTypeService.deleteOssType(ossTypeIdx));
    }

    @Operation(summary = "OSS 타입 상세", description = "oss Type 상세정보")
    @GetMapping("/{ossTypeIdx}")
    public ResponseWrapper<OssTypeDto> detailOssType(@PathVariable Long ossTypeIdx) {
        return new ResponseWrapper<>(ossTypeService.detailOssType(ossTypeIdx));
    }
}
