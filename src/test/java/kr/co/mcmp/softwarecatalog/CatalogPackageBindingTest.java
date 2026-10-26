package kr.co.mcmp.softwarecatalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;
import kr.co.mcmp.softwarecatalog.application.repository.PackageInfoRepository;

class CatalogPackageBindingTest {
    @Test void bindsTheExactImportedPackageWithoutLookingUpASeededPackageWithTheSameName() {
        var service = mock(CatalogService.class, CALLS_REAL_METHODS);
        var repository = mock(PackageInfoRepository.class);
        ReflectionTestUtils.setField(service, "packageInfoRepository", repository);
        var imported = PackageInfo.builder().id(99L).packageName("nginx").packageVersion("1.29")
                .artifactSource(ArtifactSource.NEXUS).repositoryUrl("registry.test:5500/nginx:1.29").build();
        when(repository.findById(99L)).thenReturn(Optional.of(imported));
        var catalog = new SoftwareCatalog();
        var request = SoftwareCatalogDTO.builder().target("VM").packageName("nginx").version("1.29")
                .packageInfoId(99L).build();

        ReflectionTestUtils.invokeMethod(service, "updateCatalogMappings", catalog, request);

        assertThat(catalog.getPackageInfo()).isSameAs(imported);
        assertThat(imported.getCatalog()).isSameAs(catalog);
        verify(repository, never()).findByPackageNameAndPackageVersion(any(), any());
    }

    @Test void refusesToCreateACompleteCatalogForAnUnavailableImportedPackage() {
        var service = mock(CatalogService.class, CALLS_REAL_METHODS);
        var repository = mock(PackageInfoRepository.class);
        ReflectionTestUtils.setField(service, "packageInfoRepository", repository);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var request = SoftwareCatalogDTO.builder().target("VM").packageName("nginx").version("1.29")
                .packageInfoId(99L).build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "updateCatalogMappings", new SoftwareCatalog(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unavailable");
    }
}
