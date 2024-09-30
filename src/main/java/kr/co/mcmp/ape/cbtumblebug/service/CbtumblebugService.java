package kr.co.mcmp.ape.cbtumblebug.service;

import java.util.List;

import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;

public interface CbtumblebugService {

    
    List<NamespaceDto> getAllNamespaces();

    List<MciDto> getMcisByNamespace(String namespace);

    String getK8sClusterInfo();

    String getK8sClusterByNamespace(String namespace);
}
