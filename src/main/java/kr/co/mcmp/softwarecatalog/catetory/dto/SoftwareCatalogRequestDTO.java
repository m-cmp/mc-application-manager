package kr.co.mcmp.softwarecatalog.catetory.dto;

import kr.co.mcmp.softwarecatalog.application.constants.PackageType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class SoftwareCatalogRequestDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchCatalogListDTO {
        private PackageType target;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchPackageListDTO {
        private PackageType target;
        private String category;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchPackageVersionListDTO {
        private PackageType target;
        private String applicationName;
    }
}
