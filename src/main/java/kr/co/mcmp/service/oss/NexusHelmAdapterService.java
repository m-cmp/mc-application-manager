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
public class NexusHelmAdapterService implements NexusAdapterService {

    private final NexusRepositoryAdapterClient repositoryAdapterClient;

    @Override
    public NexusRepositoryDto.ResGetRepositoryDto getRepositoryByName(NexusFormatType formatType, String name) {
        return Optional.ofNullable(repositoryAdapterClient.getRepositoryByName(formatType, name))
                .orElseThrow(() -> new IllegalArgumentException("레포지토리 " + name + "을(를) 찾을 수 없습니다."));
    }

    @Override
    public void createRepository(NexusFormatType formatType, NexusRepositoryDto.ReqCreateRepositoryDto repositoryDto) {
        boolean duplicateNameCheck = Optional.ofNullable(repositoryAdapterClient.getRepositoryList())
                .orElse(Collections.emptyList()).stream()
                .noneMatch(r -> r.getName().equals(repositoryDto.getName()));

        if (duplicateNameCheck) {
            repositoryAdapterClient.createRepository(formatType, repositoryDto);
        }
        throw new IllegalArgumentException("중복된 이름의 레포지토리가 존재합니다.");
    }
}
