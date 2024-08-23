package kr.co.mcmp.service.oss.component;

import kr.co.mcmp.service.oss.component.nexus.NexusComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommonComponentFactory {

    private final NexusComponentService nexusComponentService;

    public CommonComponentService generatedComponentService(String module) {
        switch (module) {
            case "nexus":
                return nexusComponentService;
            default:
                throw new IllegalArgumentException("Unsupported module: " + module);
        }
    }
}
