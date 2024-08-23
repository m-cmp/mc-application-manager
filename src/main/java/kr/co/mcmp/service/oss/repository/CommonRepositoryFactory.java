package kr.co.mcmp.service.oss.repository;

import kr.co.mcmp.service.oss.repository.nexus.NexusRepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommonRepositoryFactory {

    private final NexusRepositoryService nexusRepositoryService;

    public CommonRepositoryService generatedRepositoryService(String module) {
        switch (module) {
            case "nexus":
                return nexusRepositoryService;
            default:
                throw new IllegalArgumentException("Unsupported module: " + module);
        }
    }
}
