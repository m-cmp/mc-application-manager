package kr.co.mcmp.oss.nexus.service;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.nexus.api.NexusStatusApi;
import kr.co.mcmp.oss.nexus.exception.NexusException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NexusService {
	
	@Autowired
	private NexusStatusApi statusApi;

	/**
	 * 넥서스 URL 연결 체크
	 * @param nexus
	 * @return
	 */
	public boolean checkNexusConnection(OssDto nexus) {
		boolean checked = false;
		try {
			HttpStatus httpStatus = statusApi.statusEndpoint(nexus);
			if ( httpStatus == HttpStatus.OK ) {
				checked = true;
			}			
		} catch (NexusException e) {
			log.error("[getNexusRepositoryUrl] nexus error : {}", e.getMessage()); 
		} catch (Exception e) {
			log.error("[getNexusRepositoryUrl] error : {}", e.getMessage()); 
		} 
		
		return checked;
	}
}
