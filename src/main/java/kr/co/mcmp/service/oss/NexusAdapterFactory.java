package kr.co.mcmp.service.oss;

import kr.co.mcmp.dto.oss.NexusFormatType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NexusAdapterFactory {

    private final NexusRawAdapterService rawAdapterService;
    private final NexusDockerAdapterService dockerAdapterService;
    private final NexusHelmAdapterService helmAdapterService;

    public NexusAdapterService getAdapterService(NexusFormatType formatType) {
        if ("raw".equals(formatType.getFormat())) {
            return rawAdapterService;
        } else if ("docker".equals(formatType.getFormat())) {
            return dockerAdapterService;
        } else if ("helm".equals(formatType.getFormat())) {
            return helmAdapterService;
        }
        throw new IllegalArgumentException("unknown format: " + formatType.getFormat());
    }
}
