package kr.co.mcmp.softwarecatalog.kubernetes.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class KubeConfigProviderFactory {
    private final List<KubeConfigProvider> providers;

    public KubeConfigProviderFactory(List<KubeConfigProvider> providers) {
        this.providers = providers;
    }

    public KubeConfigProvider getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported CSP: " + providerName));
    }

    public static String replaceUrlHostByPort(String text, String checkPort, List<String> checkHost, String newHost) {
        String urlRegex = "(?<protocol>https?)://(?<host>[^:/\\s]+)(:(?<port>\\d+))?(?<filePart>/[^\\s]*)?";
        Pattern pattern = Pattern.compile(urlRegex);
        Matcher matcher = pattern.matcher(text);

        StringBuffer resultBuffer = new StringBuffer();
        while (matcher.find()) {
            String host = matcher.group("host").toLowerCase();
            String port = matcher.group("port");
            if (port != null && port.equals(checkPort) && checkHost.contains(host)) {
                String protocol = matcher.group("protocol");
                String filePart = matcher.group("filePart") != null ? matcher.group("filePart") : "";
                matcher.appendReplacement(resultBuffer, Matcher.quoteReplacement(protocol + "://" + newHost + ":" + checkPort + filePart));
            } else {
                matcher.appendReplacement(resultBuffer, matcher.group(0));
            }
        }
        matcher.appendTail(resultBuffer);

        return resultBuffer.toString();
    }

}