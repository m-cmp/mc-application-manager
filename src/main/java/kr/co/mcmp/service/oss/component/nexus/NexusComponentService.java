package kr.co.mcmp.service.oss.component.nexus;

import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import kr.co.mcmp.service.oss.component.CommonComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NexusComponentService implements CommonComponentService {

    private final NexusComponentAdapterService componentAdapterService;

    @Override
    public List<CommonComponent.ComponentDto> getComponentList(String name) {
        return componentAdapterService.getComponentList(name);
    }

    @Override
    public CommonComponent.ComponentDto getComponentDetailByName(String id) {
        return componentAdapterService.getComponentDetailByName(id);
    }

    @Override
    public void deleteComponent(String id) {
        componentAdapterService.deleteComponent(id);
    }

    @Override
    public void createComponent(String name, String directory, List<MultipartFile> files) {

        List<CommonUploadComponent.FilesDto> uploadFileList = new ArrayList<>();

        for (MultipartFile file : files) {
            CommonUploadComponent.FilesDto filesDto = CommonUploadComponent.FilesDto.builder()
                    .file(file)
                    .filename(file.getOriginalFilename())
                    .build();

            uploadFileList.add(filesDto);
        }

        CommonUploadComponent uploadComponent = CommonUploadComponent.builder()
                .directory(directory)
                .asset(uploadFileList)
                .build();

        componentAdapterService.createComponent(name, uploadComponent);
    }
}

