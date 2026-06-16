package kr.co.mcmp.softwarecatalog.kubernetes.config;

import org.springframework.stereotype.Component;

@Component
public class TencentKubeConfigProvider extends RawKubeConfigProvider {
    public TencentKubeConfigProvider() {
        super("Tencent");
    }

    @Override
    public boolean supports(String providerName) {
        return matches(providerName, "tencent", "tencentcloud", "tencent-cloud");
    }
}
