package kr.co.mcmp.service.oss.component;

import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CommonComponentService {

    List<CommonComponent.ComponentDto> getComponentList(String name);

    CommonComponent.ComponentDto getComponentDetailByName(String id);

    void deleteComponent(String id);

    void createComponent(String name, String directory, List<MultipartFile> files);

    void createComponentByText(String name, CommonUploadComponent.TextComponentDto textComponent);
}
