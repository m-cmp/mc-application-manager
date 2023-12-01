package m.cmp.appManager.argocd.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import m.cmp.appManager.argocd.api.model.ArgocdApplication;
import m.cmp.appManager.argocd.model.ArgocdConfig;

@Component
public class ApplicationApi {

	private static final String ARGOCD_BASIC_PATH = "/api/v1";

    @Autowired
    private RestClient client;

    private UriComponentsBuilder getUriBuilder(String url, String path) {
    	return UriComponentsBuilder.fromHttpUrl(url).pathSegment(ARGOCD_BASIC_PATH, path);
    }

    public <T> Object createApplication(ArgocdConfig config, ArgocdApplication application, Class<T> clazz) {
    	final String path = "applications" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).build().toUriString();
    	
        return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.POST, application, clazz);
    }

    public <T> Object getApplication(ArgocdConfig config, String name, Class<T> clazz) {
    	final String path = "applications/{name}";
		String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(name).toUriString();
		
		return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.GET, null, clazz);
    }

    public <T> Object deleteApplication(ArgocdConfig config, String name) {
    	final String path = "applications/{name}";
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(name).toUriString();

    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.DELETE, null, null);
    }

    public <T> Object updateHelmChartApplication(ArgocdConfig config, String name, ArgocdApplication application, Class<T> clazz) {
    	final String path = "applications/{name}" ;
    	String url = getUriBuilder(config.getArgocdUrl(), path).buildAndExpand(name).toUriString();
    	
    	return client.requestWithTokenAuth(config.getArgocdToken(), url, HttpMethod.PUT, application, clazz);
    }
}
