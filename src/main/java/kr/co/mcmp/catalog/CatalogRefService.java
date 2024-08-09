package kr.co.mcmp.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CatalogRefService {

    Logger logger = LoggerFactory.getLogger(CatalogRefService.class);

    @Autowired
    CatalogRepository catalogRepository;

    @Autowired
    CatalogRefRepository catalogRefRepository;

    public CatalogRefDTO createCatalogRef(CatalogRefDTO crDto){
        CatalogRefEntity crEntity = new CatalogRefEntity(crDto);
        crEntity = catalogRefRepository.save(crEntity);
        crDto.setCatalogRefIdx(crEntity.getId());
        return crDto;
    }

    public boolean deleteCatalogRef(CatalogRefDTO crDto){
        catalogRefRepository.deleteById(crDto.getCatalogRefIdx());
        return true;
    }


}
