package kr.co.mcmp.oss.nexus.api;

import kr.co.mcmp.oss.dto.OssDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NexusStatusApi {

	@Autowired
	private NexusRestClient client;

	public HttpStatus statusEndpoint(OssDto nexus) {
		final String path = "v1/status";
		String url = client.getUriBuilder(nexus.getOssUrl(), path).build().toUriString();
		log.debug("endpoint url : {}", url);
		
		return client.checkNexusConnection(url, HttpMethod.GET);
	}

}
