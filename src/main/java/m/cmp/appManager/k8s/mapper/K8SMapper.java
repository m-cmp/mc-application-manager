package m.cmp.appManager.k8s.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import m.cmp.appManager.argocd.model.ArgocdConfig;
import m.cmp.appManager.k8s.model.K8SConfig;

@Mapper
public interface K8SMapper {

	public List<Map<String, Object>> selectK8SCount(@Param("providerCd") String providerCd);
	
	public List<K8SConfig> selectK8SList(@Param("providerCd") String providerCd);
	
	public K8SConfig selectK8S(@Param("k8sId") int k8sId);
	
	public int insertK8S(K8SConfig k8s);
	
	public void updateK8S(K8SConfig k8s);
	
	public void deleteK8S(@Param("k8sId") int k8sId);
	
	public int isNameDuplicate(@Param("k8sName") String k8sName);
	
	public ArgocdConfig selectArgoCd(@Param("k8sId") int k8sId);
}
