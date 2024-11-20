package kr.co.mcmp.softwarecatalog.Ref;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kr.co.mcmp.softwarecatalog.CatalogRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogRefService {

    Logger logger = LoggerFactory.getLogger(CatalogRefService.class);

    private final CatalogRepository catalogRepository;

    private final CatalogRefRepository catalogRefRepository;

    // public CatalogRefDTO createCatalogRef(CatalogRefDTO crDto){
    //     CatalogRefEntity crEntity = new CatalogRefEntity(crDto);
    //     crEntity = catalogRefRepository.save(crEntity);
    //     crDto.setCatalogRefIdx(crEntity.getId());
    //     return crDto;
    // }

    // public boolean deleteCatalogRef(CatalogRefDTO crDto){
    //     catalogRefRepository.deleteById(crDto.getCatalogRefIdx());
    //     return true;
    // }


}
