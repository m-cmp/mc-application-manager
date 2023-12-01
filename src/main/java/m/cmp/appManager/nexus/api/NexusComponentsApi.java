package m.cmp.appManager.nexus.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import m.cmp.appManager.oss.model.Oss;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NexusComponentsApi {

	@Autowired
	private NexusRestClient client;

	public <T> Object listComponents(Oss nexus, String repositoryName, Class<T> clazz) {
		final String path = "/v1/components?repository="+repositoryName;
		String url = client.getUriBuilder(nexus.getOssUrl(), path).build().toUriString();
		log.debug("nexus components url : {}", url);
		
		return client.request(nexus, url, HttpMethod.GET, null, clazz);
	}
}
