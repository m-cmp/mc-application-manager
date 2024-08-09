package kr.co.mcmp.externalrepo.model;

import lombok.Data;

import java.util.List;

@Data
public class ArtifactHubPackage {

    private List<Package> packages;

    @Data
    public static class Package{

        private String package_id;
        private String name;
        private String normalized_name;
        private String description;
        private String has_values_schema;
        private Repository repository;

    }

    @Data
    public static class Repository{
        private String url;
        private String name;
        private boolean official;
        private String display_name;
        private String organization_display_name;
    }

}
