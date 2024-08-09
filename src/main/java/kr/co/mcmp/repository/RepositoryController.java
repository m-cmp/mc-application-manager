package kr.co.mcmp.repository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name="repository create, update, etc...", description="repository(현재는 nexus)관련 폴더생성, 변경 등")
@RestController
@RequestMapping("/repository")
public class RepositoryController {

    Logger logger = LoggerFactory.getLogger(RepositoryController.class);

    @Operation(summary = "get repository list")
    @GetMapping("/")
    public boolean getRepositoryList(){
        return false;
    }

    @Operation(summary = "get repository details")
    @GetMapping("/{repositoryName}")
    public boolean getRepository(@PathVariable String repositoryName){
        return false;
    }

    @Operation(summary = "create repository")
    @PostMapping("/")
    public boolean createRepository(){
        return false;
    }

    @Operation(summary = "repository delete")
    @DeleteMapping("/")
    public boolean deleteRepository(){
        return false;
    }

    @Operation(summary = "update repository")
    @PutMapping("/")
    public boolean updateRepository(){
        return false;
    }

    @Operation(summary = "insert file")
    @PostMapping("/{repositoryName}")
    public boolean insertRepository(){
        return false;
    }

}
