package kr.co.mcmp.service.oss.component.nexus;

import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import kr.co.mcmp.dto.oss.repository.CommonRepository;
import kr.co.mcmp.service.oss.repository.nexus.NexusRepositoryAdapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NexusComponentAdapterService {

    private final NexusComponentAdapterClient componentAdapterClient;
    private final NexusRepositoryAdapterService nexusRepositoryAdapterService;

    public List<CommonComponent.ComponentDto> getComponentList(String name) {
        return componentAdapterClient.getComponentList(name);
    }

    public CommonComponent.ComponentDto getComponentDetailByName(String id) {
        return componentAdapterClient.getComponentDetailByName(id);
    }

    public void deleteComponent(String id) {
        componentAdapterClient.deleteComponent(id);
    }

    public void createComponent(String name, CommonUploadComponent uploadComponent) {
        CommonRepository.RepositoryDto repositoryByName = nexusRepositoryAdapterService.getRepositoryByName(name);
        String format = repositoryByName.getFormat();

        MultiValueMap<String, Object> uploadComponentMap = getUploadComponentMap(uploadComponent, format);

        componentAdapterClient.createComponent(name, uploadComponentMap);
    }

    private static MultiValueMap<String, Object> getUploadComponentMap(CommonUploadComponent uploadComponent, String format) {
        MultiValueMap<String, Object> uploadComponentMap = new LinkedMultiValueMap<>();

        if ("raw".equals(format)) {
            uploadComponentMap.add(format + ".directory", uploadComponent.getDirectory());

            for (int i = 0; i < uploadComponent.getAsset().size(); i ++) {
                CommonUploadComponent.FilesDto filesDto = uploadComponent.getAsset().get(i);
                uploadComponentMap.add(format + ".asset" + (i + 1), filesDto.getFile().getResource());
                uploadComponentMap.add(format + ".asset" + (i + 1) + ".filename", filesDto.getFilename());
            }
        } else if ("docker".equals(format) || "helm".equals(format)) {
            CommonUploadComponent.FilesDto filesDto = uploadComponent.getAsset().get(0);
            uploadComponentMap.add(format + ".asset", filesDto.getFile().getResource());
        }
        return uploadComponentMap;
    }
}
