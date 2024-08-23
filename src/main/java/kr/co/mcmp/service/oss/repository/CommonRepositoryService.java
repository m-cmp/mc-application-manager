package kr.co.mcmp.service.oss.repository;

import kr.co.mcmp.dto.oss.repository.CommonRepository;

import java.util.List;

public interface CommonRepositoryService {

    List<CommonRepository.RepositoryDto> getRepositoryList();

    CommonRepository.RepositoryDto getRepositoryByName(String name);

    CommonRepository.RepositoryDto getRepositoryDetailByName(String name);

    void createRepository(CommonRepository.RepositoryDto repositoryDto);

    void updateRepository(CommonRepository.RepositoryDto repositoryDto);

    void deleteRepository(String name);
}
