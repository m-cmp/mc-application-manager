package kr.co.mcmp.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kr.co.mcmp.catalog.Ref.CatalogRefDTO;
import kr.co.mcmp.catalog.Ref.CatalogRefEntity;
import kr.co.mcmp.catalog.Ref.CatalogRefRepository;
import kr.co.mcmp.catalog.Ref.CatalogRefService;
import kr.co.mcmp.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogRepository catalogRepository;
    
    private final CatalogRefRepository catalogRefRepository;

    private final CatalogRefService catalogRefService;

    /**
     * List<Catalog> 조회
     * @return List<CatalogDTO>
     */
    public List<CatalogDTO> getCatalogList(){
        return catalogRepository.findAll()
                    .stream()
                    .map(ce -> {
                        CatalogDTO dto = new CatalogDTO(ce);
                        dto.setCatalogRefData(getCatalogDetail(dto.getCatalogIdx())); 
                        return dto;
                    }).collect(Collectors.toList());
    }

    /**
     * catalogIdx 검색 CatalogDTO 조회 
     * @param catalogIdx
     * @return catalogIdx 일치하는 CatalogDTO 
     */
    public CatalogDTO getCatalog(Integer catalogIdx){
        return Optional.ofNullable(catalogIdx)
            .map(idx -> {
                CatalogEntity cEntity = catalogRepository.findById(idx).orElseThrow(() -> new EntityNotFoundException("nothing findById from catalogIdx") );
                CatalogDTO cDto = new CatalogDTO(cEntity);
                cDto.setCatalogRefData(getCatalogDetail(idx));
                return cDto;
            }).orElseThrow(() -> new IllegalArgumentException("catalog index is null"));
    }

    /**
     * 제목 키워드 검색을 통한 CatalogDTO 목록 조회
     * @param keyword
     * @return 키워드와 일치하는 제목의 CatalogDTO List
     */
    public List<CatalogDTO> getCatalogListSearch(String keyword){
        return catalogRepository.findByTitleLikeIgnoreCase("%" + keyword + "%")
                .stream()
                .map(ce -> {
                    CatalogDTO dto = new CatalogDTO(ce);
                    dto.setCatalogRefData(getCatalogDetail(dto.getCatalogIdx()));
                    return dto;
                }).collect(Collectors.toList());
    }

    /**
     * CatalogRefDTO 조회
     * @param catalogIdx
     * @return List<CatalogRefDTO>
     */
    public List<CatalogRefDTO> getCatalogDetail(Integer catalogIdx){
        // TODO JPA join 필요..
        return Optional.ofNullable(catalogRefRepository.findByCatalogId(catalogIdx))
                                            .orElseGet(Collections::emptyList)
                                            .stream()
                                            .sorted(Comparator.comparing(CatalogRefEntity::getId))
                                            .map(CatalogRefDTO::new)
                                            .collect(Collectors.toList());
    }

    /**
     * catalog, catalogRef 생성
     * @param cDto
     * @param iconFile
     * @return CatalogDTO
     */
    @Transactional
    public CatalogDTO createCatalog(CatalogDTO cDto, MultipartFile iconFile){
        try {
            Optional.ofNullable(iconFile)
                .filter(file -> !file.isEmpty())
                .ifPresent(file -> {
                    try {
                        String iconPath = FileUtils.uploadIcon(iconFile);
                        cDto.setCatalogIcon(iconPath);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to upload icon", e);
                    }
                });

            CatalogEntity cEntity = catalogRepository.save(new CatalogEntity(cDto));
            cDto.setCatalogIdx(cEntity.getId());

            Optional.ofNullable(cDto.getCatalogRefData())
                .filter(refData -> !refData.isEmpty())
                .ifPresent(refData ->{
                    List<CatalogRefEntity> refEntities = refData.stream().map(crDto -> {
                        crDto.setCatalogIdx(cEntity.getId());
                        return new CatalogRefEntity(crDto);
                    }).toList();  
                    catalogRefRepository.saveAll(refEntities);
                });
            return cDto;
        } catch (UncheckedIOException e) {
            log.error("Error creating catalog : {}", e.getMessage());
            throw new IllegalArgumentException("Failed to create catalog", e);
        }
    }


    /**
     * Catalog, CatalogRef 삭제
     * @param catalogIdx
     * @return Boolean
     */
    @Transactional
    public boolean deleteCatalog(Integer catalogIdx){
        return catalogRepository.findById(catalogIdx)
        .map(catalog -> {
            /* CatalogRef 항목들 삭제 */
            catalogRefRepository.deleteAllByCatalogId(catalogIdx);
            /* Catalog 삭제 */
            catalogRepository.delete(catalog);
            
            return true;
        }).orElseThrow(() -> new EntityNotFoundException("Catalog not found with id: " + catalogIdx));
    }

    /**
     * Catalog, catalogRef 수정
     * @param catalogDto
     * @return Boolean
     */
    @Transactional
    public boolean updateCatalog(CatalogDTO catalogDto){
        return catalogRepository.findById(catalogDto.getCatalogIdx())
        .map(catalogEntity ->{
            CatalogEntity updateEntity = catalogRepository.save(new CatalogEntity(catalogDto));
            /* CatalogRef 항목들 삭제 */
            catalogRefRepository.deleteAllByCatalogId(updateEntity.getId());

            if(catalogDto.getCatalogRefData() != null){
                List<CatalogRefEntity> newRefs = catalogDto.getCatalogRefData().stream().map(CatalogRefEntity::new).collect(Collectors.toList());
                catalogRefRepository.saveAll(newRefs);
            }
            return true;
        }).orElseThrow(() -> new EntityNotFoundException("catalog not found with id : " + catalogDto.getCatalogIdx()));
    }

    @Transactional
    public boolean updateCatalog(CatalogDTO catalogDto, MultipartFile iconFile) {
        return catalogRepository.findById(catalogDto.getCatalogIdx())
            .map(catalogEntity -> {
                // 기존 아이콘 파일 삭제
                if (catalogEntity.getIcon() != null && !catalogEntity.getIcon().isEmpty()) {
                    FileUtils.deleteFile(catalogEntity.getIcon());
                }

                // 새 아이콘 파일 업로드
                Optional.ofNullable(iconFile)
                    .filter(file -> !file.isEmpty())
                    .ifPresent(file -> {
                        try {
                            String iconPath = FileUtils.uploadIcon(file);
                            catalogDto.setCatalogIcon(iconPath);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to upload new icon", e);
                        }
                    });

                CatalogEntity updateEntity = catalogRepository.save(new CatalogEntity(catalogDto));

                // CatalogRef 항목들 삭제
                catalogRefRepository.deleteAllByCatalogId(updateEntity.getId());

                // 새 CatalogRef 항목들 저장
                if (catalogDto.getCatalogRefData() != null) {
                    List<CatalogRefEntity> newRefs = catalogDto.getCatalogRefData().stream()
                        .map(refDto -> {
                            refDto.setCatalogIdx(updateEntity.getId());
                            return new CatalogRefEntity(refDto);
                        })
                        .collect(Collectors.toList());
                    catalogRefRepository.saveAll(newRefs);
                }
                return true;
            })
            .orElseThrow(() -> new EntityNotFoundException("Catalog not found with id: " + catalogDto.getCatalogIdx()));
    }

}

