package kr.co.mcmp.service.oss;

import kr.co.mcmp.dto.oss.NexusFormatType;
import kr.co.mcmp.dto.oss.NexusRepositoryDto;
import kr.co.mcmp.exception.AlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class NexusRawAdapterService implements NexusAdapterService {

    private final NexusRepositoryAdapterClient repositoryAdapterClient;

    @Override
    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryByName(NexusFormatType formatType, String name) {
        return repositoryAdapterClient.getRepositoryByName(formatType, name);
    }

    @Override
    public void createRepository(NexusFormatType formatType, NexusRepositoryDto.ReqCreateRepositoryDto repositoryDto) {
        NexusRepositoryDto.ResGetRepositoryDto repositoryOne = repositoryAdapterClient.getRepositoryOne(repositoryDto.getName());

        if (repositoryOne != null) {
            repositoryAdapterClient.createRepository(formatType, repositoryDto);
        }
    }

}
