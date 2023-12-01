package m.cmp.appManager.k8s.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import m.cmp.appManager.k8s.mapper.K8SMapper;
import m.cmp.appManager.k8s.model.K8SConfig;
import m.cmp.appManager.util.AES256Util;
import m.cmp.appManager.util.Base64Utils;

@Service
public class K8SService {

	@Autowired
	private K8SMapper k8sMapper;

	//카운트
	public List<Map<String, Object>> getK8SCount(String providerCd) {
		return k8sMapper.selectK8SCount(providerCd);
	}

	//목록
	public List<K8SConfig> getK8SList(String providerCd) {
		return k8sMapper.selectK8SList(providerCd);
	}

	//상세
	public K8SConfig getK8S(int k8sId) {
		K8SConfig k8s = k8sMapper.selectK8S(k8sId);
		
		if ( StringUtils.isNotBlank(k8s.getArgocdPassword()) ) {
			k8s.setArgocdPassword(Base64Utils.base64Encoding(AES256Util.decrypt(k8s.getArgocdPassword())));
		}
		if ( StringUtils.isNotBlank(k8s.getArgocdToken()) ) {
			k8s.setArgocdToken(Base64Utils.base64Encoding(AES256Util.decrypt(k8s.getArgocdToken())));
		}
		
		return k8s;
	}

	//등록
	public int createK8S(K8SConfig k8s) {
		if ( StringUtils.isNotBlank(k8s.getArgocdPassword()) ) {
			k8s.setArgocdPassword(AES256Util.encrypt(Base64Utils.base64Decoding(k8s.getArgocdPassword())));
		}
		if ( StringUtils.isNotBlank(k8s.getArgocdToken()) ) {
			k8s.setArgocdToken(AES256Util.encrypt(Base64Utils.base64Decoding(k8s.getArgocdToken())));
		}
		
		k8sMapper.insertK8S(k8s);
		return k8s.getK8sId();
	}

	//수정
	public int updateK8S(K8SConfig k8s) {
		if ( StringUtils.isNotBlank(k8s.getArgocdPassword()) ) {
			k8s.setArgocdPassword(AES256Util.encrypt(Base64Utils.base64Decoding(k8s.getArgocdPassword())));
		}
		if ( StringUtils.isNotBlank(k8s.getArgocdToken()) ) {
			k8s.setArgocdToken(AES256Util.encrypt(Base64Utils.base64Decoding(k8s.getArgocdToken())));
		}
		
		k8sMapper.updateK8S(k8s);
		return k8s.getK8sId();
	}

	//삭제
	public void deleteK8S(int k8sId) {
		k8sMapper.deleteK8S(k8sId);
	}
	
	//중복확인
	public boolean isNameDuplicate(String name) {
		int cnt = k8sMapper.isNameDuplicate(name);
		return (cnt > 0) ? true : false;
	}
}
