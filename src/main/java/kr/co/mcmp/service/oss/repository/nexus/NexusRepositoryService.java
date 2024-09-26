package kr.co.mcmp.service.oss.repository.nexus;

import kr.co.mcmp.dto.oss.repository.CommonFormatType;
import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.CommonRepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NexusRepositoryService implements CommonRepositoryService {

    private final NexusRepositoryAdapterService repositoryAdapterService;

    @Override
    public List<CommonRepository.RepositoryDto> getRepositoryList() {
        return repositoryAdapterService.getRepositoryList().stream()
                .filter(t -> "hosted".equals(t.getType()) &&
                        ("docker".equals(t.getFormat()) ||
                                "raw".equals(t.getFormat()) ||
                                "helm".equals(t.getFormat())))
                .collect(Collectors.toList());
    }


    @Override
    public CommonRepository.RepositoryDto getRepositoryByName(String name) {
        return repositoryAdapterService.getRepositoryByName(name);
    }

    @Override
    public CommonRepository.RepositoryDto getRepositoryDetailByName(String name) {
        CommonRepository.RepositoryDto repositoryByName = getRepositoryByName(name);

        CommonFormatType formatType = CommonFormatType.builder()
                .format(repositoryByName.getFormat())
                .type(repositoryByName.getType())
                .build();

        return repositoryAdapterService.getRepositoryDetailByName(formatType, name);
    }

    @Override
    public void createRepository(CommonRepository.RepositoryDto repositoryDto) {
        repositoryAdapterService.createRepository(repositoryDto);
    }

    @Override
    public void updateRepository(CommonRepository.RepositoryDto repositoryDto) {
        repositoryAdapterService.updateRepository(repositoryDto);
    }

    @Override
    public void deleteRepository(String name) {
        repositoryAdapterService.deleteRepository(name);
    }
}
