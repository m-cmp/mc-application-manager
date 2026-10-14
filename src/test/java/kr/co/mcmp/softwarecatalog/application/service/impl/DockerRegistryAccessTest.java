package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DockerRegistryAccessTest {
    @Test void preservesLegacyHostPortAndSecureFlag() {
        var http = DockerRegistryAccess.endpoint("", "http://nexus:8081/", 5000, false);
        assertThat(http.authority()).isEqualTo("nexus:5000");
        assertThat(http.secure()).isFalse();
        assertThat(DockerRegistryAccess.endpoint(null, "http://nexus:8081/", 5443, true).secure()).isTrue();
    }
    @Test void overrideSeparatesEngineEndpointFromRestAddress() {
        var result = DockerRegistryAccess.endpoint(" http://192.0.2.10:5500/ ", "http://nexus:8081", 5000, true);
        assertThat(result.authority()).isEqualTo("192.0.2.10:5500");
        assertThat(result.secure()).isFalse();
        assertThat(DockerRegistryAccess.endpoint("https://registry.example", "http://nexus:8081", 5000, false).authority())
                .isEqualTo("registry.example:443");
    }
    @ParameterizedTest @ValueSource(strings = {"registry:5500", "ftp://registry:5500", "http://user:pass@registry:5500",
            "http://registry:5500/repository/foo", "http://registry:5500?x=y", "http://registry:5500#x", "http://registry:99999"})
    void rejectsUnsafeOrAmbiguousOverrides(String url) {
        assertThatIllegalArgumentException().isThrownBy(() -> DockerRegistryAccess.endpoint(url, "http://nexus:8081", 5000, false));
    }
    @Test void defaultLoopbackIsNotPermissionForEveryHttpRegistry() throws Exception {
        String config = "{\"InsecureRegistryCIDRs\":[\"127.0.0.0/8\"],\"IndexConfigs\":{}}";
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "192.0.2.10:5500")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "nexus:5500")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "127.0.0.1:5500")).isTrue();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "localhost:5500")).isTrue();
    }
    @Test void explicitAuthorityAndCidrAreCheckedExactly() throws Exception {
        String config = "{\"IndexConfigs\":{\"nexus:5500\":{\"Secure\":false},\"secure:5500\":{\"Secure\":true}},"
                + "\"InsecureRegistryCIDRs\":[\"192.0.2.0/24\",\"invalid\",\"10.0.0.0/90\"]}";
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "nexus:5500")).isTrue();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "nexus:5000")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "secure:5500")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "192.0.2.255:5500")).isTrue();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "192.0.3.0:5500")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed(config, "999.0.2.10:5500")).isFalse();
        assertThat(DockerRegistryAccess.isHttpAllowed("null", "nexus:5500")).isFalse();
    }
}
