package m.cmp.appManager.argocd.api;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import m.cmp.appManager.argocd.model.ArgocdConfig;

@Component
public class ProjectApi {

	private static final String ARGOCD_BASIC_PATH = "/api/v1";

	@Autowired
    private RestClient client;

    private UriComponentsBuilder getUriBuilder(String url, String path) {
    	return UriComponentsBuilder.fromHttpUrl(url).pathSegment(ARGOCD_BASIC_PATH, path);
    }

	public <T> Object createProject(ArgocdConfig config, String projectName, Class<T> clazz) {    
		final String path = "projects" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).build().toUriString();
		
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> projectMap = new LinkedHashMap<>();
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        Map<String, Object> specMap = new LinkedHashMap<>();

        //metadata
        metadataMap.put("name", projectName);
        projectMap.put("metadata", metadataMap);

        //destination
        Map<String,String> destMap = new HashMap<>();
        destMap.put("server","*");
        destMap.put("namespace","*");
        destMap.put("name","*");
        
        //cluster whileList
        Map<String,String> clusterMap = new HashMap<>();
        clusterMap.put("group","*");
        clusterMap.put("kind","*");

        //namespace white List
        Map<String,String> namespaceMap = new HashMap<>();
        namespaceMap.put("group","*");
        namespaceMap.put("kind","*");

        specMap.put("description", projectName);
        specMap.put("sourceRepos", new String[] {"*"});
        specMap.put("destinations", new Object[]{destMap});
        specMap.put("clusterResourceWhitelist", new Object[]{clusterMap});
        specMap.put("namespaceResourceWhitelist", new Object[]{namespaceMap});
        projectMap.put("spec",specMap);

        map.put("project",projectMap);

        return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.POST, map, clazz);
    }

    public <T> Object getProject(ArgocdConfig config, String projectName, Class<T> clazz) {
		final String path = "projects/{name}" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(projectName).toUriString();

    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.GET, null, clazz);
    }
}
