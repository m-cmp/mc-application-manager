package m.cmp.appManager.nexus.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import m.cmp.appManager.oss.model.Oss;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NexusRepositoryApi {

	@Autowired
	private NexusRestClient client;

	public <T> Object getRepositoryDetails(Oss nexus, String repositoryName, Class<T> clazz) {
		final String path = "/v1/repositories/"+repositoryName;
		String url = client.getUriBuilder(nexus.getOssUrl(), path).build().toUriString();
		log.debug("nexus repository url : {}", url);
		
		return client.request(nexus, url, HttpMethod.GET, null, clazz);
	}
}
