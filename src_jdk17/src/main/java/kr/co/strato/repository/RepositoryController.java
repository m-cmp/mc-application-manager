package kr.co.strato.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/repository")
public class RepositoryController {

    Logger logger = LoggerFactory.getLogger(RepositoryController.class);


    @GetMapping("/")
    public String getRepositoryList(){
        return null;
    }

    @GetMapping("/{repository2Depth}")
    public String getRepositoryList1Depth(@PathVariable String repository2Depth){
        return null;
    }

    @PostMapping("/")
    public String createRepository(){
        return null;
    }

    @DeleteMapping("/")
    public String deleteRepository(){
        return null;
    }

    @PutMapping("/")
    public String updateRepository(){
        return null;
    }



}
