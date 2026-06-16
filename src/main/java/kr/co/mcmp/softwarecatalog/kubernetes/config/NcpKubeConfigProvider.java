package kr.co.mcmp.softwarecatalog.kubernetes.config;

import org.springframework.stereotype.Component;

@Component
public class NcpKubeConfigProvider extends RawKubeConfigProvider {
    public NcpKubeConfigProvider() {
        super("NCP");
    }

    @Override
    public boolean supports(String providerName) {
        return matches(providerName, "ncp", "ncloud", "naver", "navercloud", "naver-cloud",
                "ncp-vpc", "ncpvpc", "ncp-classic", "ncpclassic");
    }
}
