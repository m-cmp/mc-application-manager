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

@Tag(name = "oss", description = "OSS management")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/oss")
@RestController
public class OssController {

    private final OssService ossService;

    @Operation(summary = "Get OSS list", description = "Retrieve all OSS list" )
    @GetMapping("/list")
    public ResponseWrapper<List<OssDto>> getOssList() {
        return new ResponseWrapper<>(ossService.getAllOssList());
    }

    @Operation(summary = "Get OSS list by keyword", description = "Retrieve OSS list by keyword" )
    @GetMapping("/list/{ossTypeName}")
    public ResponseWrapper<List<OssDto>> getOssList(@PathVariable("ossTypeName") String ossTypeName) {
        return new ResponseWrapper<>(ossService.getOssList(ossTypeName));
    }

    @Operation(summary = "Register OSS", description = "Register OSS")
    @PostMapping
    public ResponseWrapper<Long> registOss(@RequestBody OssDto ossDto) {
        return new ResponseWrapper<>(ossService.registOss(ossDto));
    }

    @Operation(summary = "Update OSS", description = "Update OSS")
    @PatchMapping("/{ossIdx}")
    public ResponseWrapper<Long> updateOss(@PathVariable Long ossIdx, @RequestBody OssDto ossDto) {
        if ( ossIdx != 0 || ossDto.getOssIdx() != 0 ) {
            return new ResponseWrapper<>(ossService.updateOss(ossDto));
        }
        return new ResponseWrapper<>(ResponseCode.BAD_REQUEST, "OssIdx");
    }

    @Operation(summary = "Delete OSS", description = "Delete OSS")
    @DeleteMapping("/{ossIdx}")
    public ResponseWrapper<Boolean> deleteOss(@PathVariable Long ossIdx) {
        return new ResponseWrapper<>(ossService.deleteOss(ossIdx));
    }

    @Operation(summary = "Get OSS details", description = "Retrieve OSS details" )
    @GetMapping("/{ossIdx}")
    public ResponseWrapper<OssDto> detailOss(@PathVariable Long ossIdx) {
        return new ResponseWrapper<>(ossService.detailOss(ossIdx));
    }

    @Operation(summary = "Check OSS information duplication (oss name, url, username)", description = "true : duplicated / false : not duplicated")
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

    @Operation(summary = "Check OSS connection", description = "Check OSS connection")
    @PostMapping("/connection-check")
    public ResponseWrapper<Boolean> checkConnection(@RequestBody OssDto ossDto) {
        return new ResponseWrapper<>(ossService.checkConnection(ossDto));
    }
}
