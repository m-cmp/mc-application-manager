package kr.co.mcmp.service.oss.repository.nexus;

import kr.co.mcmp.dto.oss.repository.CommonFormatType;
import kr.co.mcmp.dto.oss.repository.CommonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NexusRepositoryAdapterService {

    private final NexusRepositoryAdapterClient repositoryAdapterClient;

    public List<CommonRepository.RepositoryDto> getRepositoryList() {
        return repositoryAdapterClient.getRepositoryList();
    }

    public CommonRepository.RepositoryDto getRepositoryByName(String name) {
        return repositoryAdapterClient.getRepositoryByName(name);
    }

    public CommonRepository.RepositoryDto getRepositoryDetailByName(CommonFormatType formatType, String name) {
        return repositoryAdapterClient.getRepositoryDetailByName(formatType, name);
    }

    public void createRepository(CommonRepository.RepositoryDto repositoryDto) {
        repositoryAdapterClient.createRepository(repositoryDto);
    }

    public void updateRepository(CommonRepository.RepositoryDto repositoryDto) {
        repositoryAdapterClient.updateRepository(repositoryDto);
    }

    public void deleteRepository(String name) {
        repositoryAdapterClient.deleteRepository(name);
    }
}
