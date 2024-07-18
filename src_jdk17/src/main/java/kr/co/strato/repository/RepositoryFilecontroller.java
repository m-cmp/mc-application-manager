package kr.co.strato.repository;

import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/repository/file")
public class RepositoryFilecontroller {

    Logger logger = LoggerFactory.getLogger(kr.co.strato.repository.RepositoryFilecontroller.class);

    @ApiOperation(value="file upload", notes="file upload")
    @PostMapping("/")
    public String uploadFiles(){
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
