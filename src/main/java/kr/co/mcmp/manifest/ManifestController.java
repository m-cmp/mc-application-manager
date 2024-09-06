package kr.co.mcmp.manifest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="manifest crud", description="설정 관련 파일들 저장, 다운로드")
@RestController
@RequestMapping("/manifest")
public class ManifestController {

    //Logger logger = LoggerFactory.getLogger(ManifestController.class);

    @Operation(summary = "get manifest list")
    @GetMapping("/")
    public List<ManifestDTO> getManifest(){
        return null;
    }

    @Operation(summary = "get manifest")
    @GetMapping("/{manifestIdx}")
    public ManifestDTO getManifestDetail(@PathVariable Integer manifestIdx){
        return null;
    }

    @Operation(summary = "get manifest(string)")
    @GetMapping("/{manifestIdx}/txt")
    public String getManifestDetailTxt(@PathVariable Integer manifestIdx){
        return null;
    }

    @Operation(summary = "insert manifest")
    @PostMapping("/")
    public ManifestDTO createManifest(ManifestDTO manifestDto){
        return null;
    }

    @Operation(summary = "update manifest")
    @PutMapping("/")
    public ManifestDTO updateManifest(ManifestDTO manifestDto){
        return null;
    }

    @Operation(summary = "delete manifest")
    @DeleteMapping("/{manifestIdx}")
    public boolean updateManifest(@PathVariable Integer manifestIdx){
        return false;
    }

    @Operation(summary = "manifest content to Files(downloads)")
    @GetMapping("/download/{manifestIdx}")
    public boolean saveManifest(@PathVariable Integer manifestIdx){
        return false;
    }

}
