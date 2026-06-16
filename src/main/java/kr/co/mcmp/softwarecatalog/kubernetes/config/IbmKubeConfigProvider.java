package kr.co.mcmp.softwarecatalog.kubernetes.config;

import org.springframework.stereotype.Component;

@Component
public class IbmKubeConfigProvider extends RawKubeConfigProvider {
    public IbmKubeConfigProvider() {
        super("IBM");
    }

    @Override
    public boolean supports(String providerName) {
        return matches(providerName, "ibm", "ibmcloud", "ibm-cloud", "ibm-vpc", "ibmvpc");
    }
}
