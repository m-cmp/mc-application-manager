package kr.co.mcmp.service.oss;

import kr.co.mcmp.dto.oss.NexusFormatType;
import kr.co.mcmp.dto.oss.NexusRepositoryDto;

public interface NexusAdapterService {

    NexusRepositoryDto.ResGetRepositoryDto getRepositoryByName(NexusFormatType formatType, String name);

    void createRepository(NexusFormatType formatType, NexusRepositoryDto.ReqCreateRepositoryDto repositoryDto);

}
