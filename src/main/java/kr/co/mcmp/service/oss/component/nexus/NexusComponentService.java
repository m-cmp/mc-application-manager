package kr.co.mcmp.service.oss.component.nexus;

import kr.co.mcmp.dto.oss.component.CommonComponent;
import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import kr.co.mcmp.service.oss.component.CommonComponentService;
import kr.co.mcmp.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
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

        List<CommonUploadComponent.FilesDto> uploadFiles = files.stream()
                .map(this::convertToFileDto)
                .collect(Collectors.toList());

        CommonUploadComponent uploadComponent = CommonUploadComponent.builder()
                .directory(directory)
                .assets(uploadFiles)
                .build();

        componentAdapterService.createComponent(name, uploadComponent);
    }

    @Override
    public void createComponentByText(String name, CommonUploadComponent.TextComponentDto textComponent) {
        try {
            MultipartFile file = FileUtil.generatedMultipartFile(textComponent);

            CommonUploadComponent.FilesDto filesDto = convertToFileDto(file);
            List<CommonUploadComponent.FilesDto> uploadFiles = Collections.singletonList(filesDto);

            CommonUploadComponent uploadComponent = CommonUploadComponent.builder()
                    .directory(textComponent.getDirectory())
                    .assets(uploadFiles)
                    .build();

            componentAdapterService.createComponent(name, uploadComponent);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private CommonUploadComponent.FilesDto convertToFileDto(MultipartFile file) {
        return CommonUploadComponent.FilesDto.builder()
                .file(file)
                .filename(file.getOriginalFilename())
                .build();
    }
}

