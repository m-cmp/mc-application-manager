package kr.co.mcmp.ape.cbtumblebug.service;

import java.util.List;

import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CbtumblebugServiceImpl implements CbtumblebugService{

    private final CbtumblebugRestApi api;

    @Override
    public List<NamespaceDto> getAllNamespaces() {
        return api.getAllNamespace();
    }

    @Override
    public List<MciDto> getMcisByNamespace(String namespace) {
        return api.getMcisByNamespace(namespace);
    }

    @Override
    public String getK8sClusterInfo() {
        return api.getK8sClusterInfo();
    }

    @Override
    public String getK8sClusterByNamespace(String namespace) {
        return api.getK8sClusterByNamespace(namespace);
    }

}
