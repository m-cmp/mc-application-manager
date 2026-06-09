package kr.co.mcmp.service.oss.repository;

import kr.co.mcmp.dto.oss.repository.CommonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommonModuleRepositoryService {


    @Value("${nexus.docker-port:${docker.registry.port:5000}}")
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
        if ("docker".equals(repositoryDto.getFormat())) {
            repositoryDto.getDocker().setHttpPort(dockerRegistryPort);
            repositoryDto.getDocker().setHttpsPort(null);
            repositoryDto.getDocker().setSubdomain(null);
            log.info("Docker repository connector port resolved: module={}, repository={}, httpPort={}",
                    module, repositoryDto.getName(), dockerRegistryPort);
        }


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
