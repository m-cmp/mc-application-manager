package m.cmp.appManager.argocd.api;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import m.cmp.appManager.argocd.api.model.Source;
import m.cmp.appManager.argocd.model.ArgocdConfig;

@Component
public class RepositoryApi {

	private static final String ARGOCD_BASIC_PATH = "/api/v1";

    @Autowired
    private RestClient client;
    
    private UriComponentsBuilder getUriBuilder(String url, String path) {
    	return UriComponentsBuilder.fromHttpUrl(url).pathSegment(ARGOCD_BASIC_PATH, path);
    }

    public <T> Object getRepository(ArgocdConfig config, String repoUrl, Class<T> clazz)  { 
    	try {
			repoUrl = URLEncoder.encode(repoUrl, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
    	
    	String path = "repositories/{repo}" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(repoUrl).toUriString();
    	
    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.GET, null, clazz);
    }

    public <T> Object getAppDetails(ArgocdConfig config, Source source, String projectName, Class<T> clazz)  { 
    	String repoUrl = source.getRepoURL();
    	try {
    		repoUrl = URLEncoder.encode(repoUrl, "UTF-8");
    	} catch (UnsupportedEncodingException e) {
    	}
    	
    	String path = "repositories/{repoURL}/appdetails" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(repoUrl).toUriString();
    	
    	Map<String, Object> map = new HashMap<>();
    	map.put("appName", 	  source.getAppName());
    	map.put("appProject", projectName);
    	map.put("source",     source);
    	
    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.POST, map, clazz);
    }

    public <T> Object deleteRepository(ArgocdConfig config, String repoUrl) {

    	try {
			repoUrl = URLEncoder.encode(repoUrl, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}

    	String path = "repositories/{repo}" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(repoUrl).toUriString();

    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.DELETE, null, null);
    }

    public <T> Object createHelmChartRepository(ArgocdConfig config, String projectName, String repoUrl, String username, String password, Class<T> clazz) {    	
    	String path = "repositories" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).build().toUriString();
    	
    	Map<String, Object> map = new HashMap<>();
    	map.put("type", 	"helm");
    	map.put("name", 	repoUrl.split("/")[2]);
    	map.put("repo", 	repoUrl);
    	map.put("project",	projectName);
    	map.put("username", username);
    	map.put("password", password);
    	map.put("insecure", true);
    	map.put("insecureIgnoreHostKey", true);

    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.POST, map, clazz);
    }
}
