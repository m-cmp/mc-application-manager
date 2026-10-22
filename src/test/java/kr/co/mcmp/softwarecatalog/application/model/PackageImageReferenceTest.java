package kr.co.mcmp.softwarecatalog.application.model;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;

class PackageImageReferenceTest {
    @Test void seededPackageKeepsDockerHubEvenWhenItsWebMetadataMentionsAnotherHost() {
        var info = PackageInfoDTO.builder().packageName("nginx").packageVersion("1.29")
                .repositoryUrl("https://hub.docker.com/_/nginx").build();
        assertThat(PackageImageReference.resolve(info)).isEqualTo("docker.io/nginx:1.29");
    }

    @Test void preservesExistingThirdPartyRegistryAndCaseSensitiveTag() {
        var info = PackageInfo.builder().packageName("quay.io/team/app").packageVersion("ReleaseA").build();
        assertThat(PackageImageReference.resolve(info)).isEqualTo("quay.io/team/app:ReleaseA");
    }

    @Test void importedPackageUsesTheStoredReferenceInsteadOfRebuildingItFromThePackageName() {
        var info = PackageInfo.builder().packageName("nginx").packageVersion("1.29")
                .artifactSource(ArtifactSource.NEXUS).repositoryUrl("registry.test:5500/library/nginx:1.29").build();
        assertThat(PackageImageReference.resolve(PackageInfoDTO.fromEntity(info)))
                .isEqualTo("registry.test:5500/library/nginx:1.29");
    }

    @ParameterizedTest
    @ValueSource(strings={"", "https://registry.test:5500/nginx:1.29", "registry.test/nginx", "user@registry.test/nginx:v1"})
    void invalidImportedReferencesFailWithoutFallingBackToDockerHub(String reference) {
        var info = PackageInfoDTO.builder().packageName("nginx").packageVersion("1.29")
                .artifactSource(ArtifactSource.NEXUS).repositoryUrl(reference).build();
        assertThatThrownBy(() -> PackageImageReference.resolve(info)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void clientsCannotClaimThatAnUnimportedPackageIsStoredInNexus() throws Exception {
        var info = new ObjectMapper().readValue("{\"artifactSource\":\"NEXUS\",\"packageName\":\"nginx\"}", PackageInfoDTO.class);
        assertThat(info.getArtifactSource()).isNull();
    }
}
