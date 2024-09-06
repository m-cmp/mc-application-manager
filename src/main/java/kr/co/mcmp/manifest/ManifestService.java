package kr.co.mcmp.manifest;

import io.swagger.v3.oas.annotations.Operation;
import kr.co.mcmp.catalog.CatalogDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class ManifestService {

    @Autowired
    ManifestRepository manifestRepository;

    public List<ManifestDTO> getManifest(){
        List<ManifestEntity> manifestEntityList = manifestRepository.findAll();
        List<ManifestDTO> manifestDtoList = new ArrayList<>();
        for(ManifestEntity ml:manifestEntityList){
            ml.setManifest(ml.getManifest().substring(0, 100));
            manifestDtoList.add(new ManifestDTO(ml));
        }
        return manifestDtoList;
    }

    public ManifestDTO getManifestDetail(Integer manifestIdx){
        ManifestEntity manifestEntity = manifestRepository.findById(manifestIdx).orElseThrow(()->new EntityNotFoundException("nothing findById from catalogIdx"));
        ManifestDTO manifestDto = new ManifestDTO(manifestEntity);
        return manifestDto;
    }

    public ManifestDTO createManifest(ManifestDTO manifestDto){
        manifestRepository.save(new ManifestEntity(manifestDto));
        return manifestDto;
    }

    public ManifestDTO updateManifest(ManifestDTO manifestDto){
        manifestRepository.save(new ManifestEntity(manifestDto));
        return manifestDto;
    }

    public boolean updateManifest(Integer manifestIdx){
        manifestRepository.deleteById(manifestIdx);
        return true;
    }


}
