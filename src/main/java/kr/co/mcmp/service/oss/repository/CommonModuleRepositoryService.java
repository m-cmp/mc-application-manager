package kr.co.mcmp.service.oss.repository;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonModuleRepositoryService {


    @Value("${docker.registry.port:5500}")
    private int dockerRegistryPort;

    private final CommonRepositoryFactory repositoryFactory;

    public List<CommonRepository.RepositoryDto> getRepositoryList(String module) {
        CommonRepositoryService repositoryService = getRepositoryService(module);
        return repositoryService.getRepositoryList();
    }

    public CommonRepository.RepositoryDto getRepositoryDetailByName(String module, String name) {
        CommonRepositoryService repositoryService = getRepositoryService(module);
        return repositoryService.getRepositoryDetailByName(name);
    }

    public void createRepository(String module, CommonRepository.RepositoryDto repositoryDto) {
        repositoryDto.getDocker().setHttpPort(dockerRegistryPort);
        repositoryDto.getDocker().setHttpsPort(dockerRegistryPort + 100);


        CommonRepositoryService repositoryService = getRepositoryService(module);
        repositoryService.createRepository(repositoryDto);
    }

    public void updateRepository(String module, CommonRepository.RepositoryDto repositoryDto) { 
        CommonRepositoryService repositoryService = getRepositoryService(module);
        repositoryService.updateRepository(repositoryDto);
    }

    public void deleteRepository(String module, String name) {
        CommonRepositoryService repositoryService = getRepositoryService(module);
        repositoryService.deleteRepository(name);
    }

    private CommonRepositoryService getRepositoryService(String module) {
        return repositoryFactory.generatedRepositoryService(module);
    }
}
