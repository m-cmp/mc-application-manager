package kr.co.mcmp.service.oss.component;

import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonModuleComponentService {

    private final CommonComponentFactory componentFactory;

    public List<CommonComponent.ComponentDto> getComponentList(String module, String name) {
        CommonComponentService componentService = getComponentService(module);
        return componentService.getComponentList(name);
    }

    public CommonComponent.ComponentDto getComponentDetailByName(String module, String id) {
        CommonComponentService componentService = getComponentService(module);
        return componentService.getComponentDetailByName(id);
    }

    public void deleteComponent(String module, String id) {
        CommonComponentService componentService = getComponentService(module);
        componentService.deleteComponent(id);
    }

    public void createComponent(String module, String name, String directory, List<MultipartFile> files) {
        CommonComponentService componentService = getComponentService(module);
        componentService.createComponent(name, directory, files);
    }

    public void createComponentByText(String module, String name, CommonUploadComponent.TextComponentDto textComponent) {
        CommonComponentService componentService = getComponentService(module);
        componentService.createComponentByText(name, textComponent);
    }

    private CommonComponentService getComponentService(String module) {
        return componentFactory.generatedComponentService(module);
    }
}
