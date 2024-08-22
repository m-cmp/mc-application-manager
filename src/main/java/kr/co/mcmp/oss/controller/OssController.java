package kr.co.mcmp.oss.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssService;
import kr.co.mcmp.response.ResponseCode;
import kr.co.mcmp.response.ResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "oss", description = "oss 관리")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/oss")
@RestController
public class OssController {

    private final OssService ossService;

    @Operation(summary = "OSS 목록 조회", description = "oss 모든 목록조회" )
    @GetMapping("/list")
    public ResponseWrapper<List<OssDto>> getOssList() {
        return new ResponseWrapper<>(ossService.getAllOssList());
    }

    @Operation(summary = "OSS 목록 조회", description = "oss 목록조회(Keyword)" )
    @GetMapping("/list/{ossTypeName}")
    public ResponseWrapper<List<OssDto>> getOssList(@PathVariable("ossTypeName") String ossTypeName) {
        return new ResponseWrapper<>(ossService.getOssList(ossTypeName));
    }

    @Operation(summary = "OSS 등록", description = "oss 등록")
    @PostMapping
    public ResponseWrapper<Long> registOss(@RequestBody OssDto ossDto) {
        return new ResponseWrapper<>(ossService.registOss(ossDto));
    }

    @Operation(summary = "OSS 수정", description = "oss 수정")
    @PatchMapping("/{ossIdx}")
    public ResponseWrapper<Long> updateOss(@PathVariable Long ossIdx, @RequestBody OssDto ossDto) {
        if ( ossIdx != 0 || ossDto.getOssIdx() != 0 ) {
            return new ResponseWrapper<>(ossService.updateOss(ossDto));
        }
        return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "OssIdx");
    }

    @Operation(summary = "OSS 삭제", description = "oss 삭제")
    @DeleteMapping("/{ossIdx}")
    public ResponseWrapper<Boolean> deleteOss(@PathVariable Long ossIdx) {
        return new ResponseWrapper<>(ossService.deleteOss(ossIdx));
    }

    @Operation(summary = "OSS 상세 조회", description = "oss 상세조회" )
    @GetMapping("/{ossIdx}")
    public ResponseWrapper<OssDto> detailOss(@PathVariable Long ossIdx) {
        return new ResponseWrapper<>(ossService.detailOss(ossIdx));
    }

    @Operation(summary = "OSS 정보 중복 체크(oss명, url, username)", description = "true : 중복 / false : 중복 아님")
    @GetMapping("/duplicate")
    public ResponseWrapper<Boolean> isOssInfoDuplicated(@RequestParam String ossName, @RequestParam String ossUrl, @RequestParam String ossUsername) {
        if ( StringUtils.isBlank(ossName) ) {
            return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossName");
        }
        else if ( StringUtils.isBlank(ossUrl) ) {
            return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossUrl");
        }
        else if ( StringUtils.isBlank(ossUsername) ) {
            return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "ossUsername");
        }
        OssDto ossDto = OssDto.setOssAttributesDuplicate(ossName, ossUrl, ossUsername);
        return new ResponseWrapper<>(ossService.isOssInfoDuplicated(ossDto));
    }

    @Operation(summary = "OSS 연결확인", description = "oss 연결 확인")
    @PostMapping("/connection-check")
    public ResponseWrapper<Boolean> checkConnection(@RequestBody OssDto ossDto) {
        return new ResponseWrapper<>(ossService.checkConnection(ossDto));
    }
}
