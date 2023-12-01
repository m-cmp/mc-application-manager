package m.cmp.appManager.catalog.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import m.cmp.appManager.api.response.ResponseWrapper;
import m.cmp.appManager.catalog.model.Catalog;
import m.cmp.appManager.catalog.service.CatalogService;

@Tag(name = "Catalog", description = "카탈로그 관리")
@RestController
public class CatalogController {
	
	@Autowired
	private CatalogService catalogService;
	
	@Operation(summary = "카탈로그 목록 조회")
	@GetMapping("/catalog/list")
	public ResponseWrapper<List<Catalog>> getCatalogList(@RequestParam int nexusId) {
		return new ResponseWrapper<>(catalogService.getCatalogList(nexusId));
	}
}
