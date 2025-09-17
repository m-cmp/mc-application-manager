package kr.co.mcmp.softwarecatalog.rating.service;

import java.util.List;
import java.util.Map;

import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingRequestDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingResponseDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.RatingSummaryDTO;

public interface SoftwareRatingService {

    /**
     * 전체 평가 등록
     */
    OverallRatingResponseDTO createOverallRating(OverallRatingRequestDTO request, String username);

    /**
     * 전체 평가 수정
     */
    OverallRatingResponseDTO updateOverallRating(Long ratingId, OverallRatingRequestDTO request, String username);

    /**
     * 전체 평가 조회
     */
    List<OverallRatingResponseDTO> getOverallRatings(Long catalogId, int page, int size);

    /**
     * 평점 요약 조회
     */
    RatingSummaryDTO getRatingSummary(Long catalogId);

    /**
     * 사용자별 평가 조회
     */
    List<OverallRatingResponseDTO> getUserRatings(String username, int page, int size);

    /**
     * 평가 통계 조회
     */
    Map<String, Object> getRatingStatistics(Long catalogId, String category);

    /**
     * 평가 삭제
     */
    void deleteOverallRating(Long ratingId, String username);
}
