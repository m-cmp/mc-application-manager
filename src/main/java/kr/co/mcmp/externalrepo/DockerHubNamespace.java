package kr.co.mcmp.externalrepo;

import lombok.Data;

import java.util.List;

@Data
public class DockerHubNamespace {

    private int count;

    private int page;
    private int page_size;
    private String next;
    private String previous;
    private List<Result> results;

    @Data
    public static class Result{
        private String name;
        private String namespace;
        private String repository_type;
        private String status_description;
        private String last_updated;
        private List<Category> categories;
    }

    @Data
    public static class Category{
        private String name;
        private String slug;
    }

}
