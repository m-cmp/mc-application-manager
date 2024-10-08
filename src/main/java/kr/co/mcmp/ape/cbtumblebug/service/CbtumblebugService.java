package kr.co.mcmp.ape.cbtumblebug.service;

import java.util.List;

import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;

public interface CbtumblebugService {

    
    List<NamespaceDto> getAllNamespaces();

    List<MciDto> getMcisByNamespace(String namespace);

    List<K8sClusterDto> getAllK8sClusters(String namespace);

    K8sClusterDto getK8sClusterByName(String namespace, String clusterName);

    MciDto getMciByMciId(String nsId, String mciId);
}
