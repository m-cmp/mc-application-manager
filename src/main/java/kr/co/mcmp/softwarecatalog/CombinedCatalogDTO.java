package kr.co.mcmp.softwarecatalog;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CombinedCatalogDTO {

    SoftwareCatalogDTO softwareCatalogDTO;
    CommonRepository.RepositoryDto repositoryDTO;
}
