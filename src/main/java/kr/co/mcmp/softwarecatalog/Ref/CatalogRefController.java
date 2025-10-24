package kr.co.mcmp.softwarecatalog.Ref;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="software catalog reference crud", description="software catalog related information (workflow, homepage, other materials) input, modification, etc.")
@RestController
@RequestMapping("/catalog/software/ref")
public class CatalogRefController {

    Logger logger = LoggerFactory.getLogger(CatalogRefController.class);

    @Autowired
    CatalogRefService catalogRefService;




}
