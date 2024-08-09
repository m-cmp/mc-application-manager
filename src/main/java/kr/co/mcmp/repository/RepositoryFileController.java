package kr.co.mcmp.repository;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name="repository file upload, download", description="repository(현재는 nexus)관련 파일 업로드, 다운로드 등")
@RestController
@RequestMapping("/repository/file")
public class RepositoryFileController {

    Logger logger = LoggerFactory.getLogger(RepositoryFileController.class);

    @ApiOperation(value="file upload", notes="file upload")
    @PostMapping("/")
    public String uploadFiles(){
        // 이미 존재하는 파일이라면 기존껀 파일명 바꿔서 백업하고 신규파일 업로드
        return null;
    }

    @GetMapping("/{filename}") // or packageName
    public String getRepositoryFile(@PathVariable String filename){
        return null;
    }

    @DeleteMapping("/{filename}") // or packageName
    public String deleteRepositoryFile(@PathVariable String filename){
        return null;
    }



}
