package kr.co.mcmp.service.oss;

import kr.co.mcmp.dto.oss.NexusFormatType;
import kr.co.mcmp.dto.oss.NexusRepositoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class NexusRepositoryService {

    private final NexusRepositoryAdapterClient repositoryAdapterClient;
    private final NexusAdapterFactory adapterFactory;

    public List<NexusRepositoryDto.ResGetRepositoryDto> getRepositoryList() {
        return repositoryAdapterClient.getRepositoryList();
    }

    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryOne(String name) {
        return repositoryAdapterClient.getRepositoryOne(name);
    }

    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryByName(NexusFormatType formatType, String name) {
        NexusAdapterService adapterService = adapterFactory.getAdapterService(formatType);
        return adapterService.getRepositoryByName(formatType, name);
    }

    public void createRepository(NexusFormatType formatType, NexusRepositoryDto.ReqCreateRepositoryDto repositoryDto) {
        NexusAdapterService adapterService = adapterFactory.getAdapterService(formatType);
        adapterService.createRepository(formatType, repositoryDto);
    }

    //public updateRepository(String name, NexusRepositoryDto.ReqUpdateRepositoryDto repositoryDto)
}
