package kr.co.mcmp.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogService {

    @Autowired
    CatalogRepository catalogRepository;

    @Autowired
    CatalogRefRepository catalogRefRepository;

    @Autowired
    CatalogRefService catalogRefService;

    public List<CatalogDTO> getCatalogList(){
        List<CatalogEntity> lcEntity = catalogRepository.findAll();
        List<CatalogDTO> lcDto = new ArrayList<>();
        for(CatalogEntity ce:lcEntity){
            lcDto.add(new CatalogDTO(ce));
        }
        return lcDto;
    }

    public List<CatalogDTO> getCatalogListSearch(String keyword){
//        List<CatalogEntity> lcEntity = catalogRepository.findbyTitle(keyword);
//        List<CatalogDTO> lcDto = new ArrayList<>();
//        for(CatalogEntity ce:lcEntity){
//            lcDto.add(new CatalogDTO(ce));
//        }
//        return lcDto;
        return null;
    }

    public CatalogDTO getCatalogDetail(Integer catalogIdx){
        CatalogEntity cEntity = catalogRepository.findById(catalogIdx).orElseThrow(()->new EntityNotFoundException("nothing findById from catalogIdx"));
        CatalogDTO cDto = new CatalogDTO(cEntity);
        List<CatalogRefEntity> crEntity = catalogRefRepository.findByCatalogId(catalogIdx);
        //List<CatalogRefEntity> crEntity = catalogRefRepository.findByCatalogIdOrderByReferenceTypeAsc(catalogIdx);
        List<CatalogRefDTO> crDto = new ArrayList<>();
        for(CatalogRefEntity cre:crEntity){
            crDto.add(new CatalogRefDTO(cre));
        }
        cDto.setCatalogRefData(crDto);
        return cDto;
    }

    public CatalogDTO createCatalog(CatalogDTO cDto){
        System.out.println("=========================" + cDto.getCatalogTitle());
        CatalogEntity cEntity = new CatalogEntity(cDto);
        System.out.println("=========================" + cEntity.getCategory());
        cEntity = catalogRepository.save(cEntity);
        System.out.println("=========================" + cEntity);
        Integer catalogIdx = cEntity.getId();
        if(cDto.getCatalogRefData() != null) {
            for (CatalogRefDTO crDto : cDto.getCatalogRefData()) {
                crDto.setCatalogIdx(catalogIdx);
                crDto = catalogRefService.createCatalogRef(crDto);
            }
        }
        return cDto;
    }



    public boolean deleteCatalog(Integer catalogIdx){
        catalogRepository.deleteById(catalogIdx);
        CatalogEntity cEntity = catalogRepository.findById(catalogIdx).orElseThrow(()->new EntityNotFoundException("nothing findById from catalogIdx"));
        if(cEntity == null){
            return true;
        }else{
            return false;
        }
    }

    public CatalogDTO updateCatalog(CatalogDTO catalogDto){
        CatalogEntity catalogEntity = new CatalogEntity(catalogDto);
        catalogEntity = catalogRepository.save(catalogEntity);
        return catalogDto;
    }


}

