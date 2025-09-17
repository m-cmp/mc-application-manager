package kr.co.mcmp.softwarecatalog.rating.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingRequestDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingResponseDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.RatingSummaryDTO;
import kr.co.mcmp.softwarecatalog.rating.entity.OverallRating;
import kr.co.mcmp.softwarecatalog.rating.repository.OverallRatingRepository;
import kr.co.mcmp.softwarecatalog.rating.service.SoftwareRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SoftwareRatingServiceImpl implements SoftwareRatingService {

    private final OverallRatingRepository overallRatingRepository;

    @Override
    public OverallRatingResponseDTO createOverallRating(OverallRatingRequestDTO request, String username) {
        log.info("Creating overall rating for catalogId: {}, username: {}", request.getCatalogId(), username);
        
        OverallRating rating = new OverallRating();
        rating.setCatalogId(request.getCatalogId());
        rating.setRating(request.getRating());
        rating.setCategory(request.getCategory());
        rating.setDetailedComments(request.getDetailedComments());
        rating.setName(request.getName());
        rating.setEmail(request.getEmail());
        rating.setUsername(username);
        rating.setMetadata(request.getMetadata());
        rating.setCreatedAt(LocalDateTime.now());
        rating.setUpdatedAt(LocalDateTime.now());

        OverallRating savedRating = overallRatingRepository.save(rating);
        
        return convertToResponseDTO(savedRating);
    }

    @Override
    public OverallRatingResponseDTO updateOverallRating(Long ratingId, OverallRatingRequestDTO request, String username) {
        log.info("Updating overall rating with id: {}, username: {}", ratingId, username);
        
        OverallRating rating = overallRatingRepository.findById(ratingId)
                .orElseThrow(() -> new RuntimeException("Rating not found with id: " + ratingId));
        
        // 사용자 권한 확인
        if (username != null && !username.equals(rating.getUsername())) {
            throw new RuntimeException("Unauthorized to update this rating");
        }

        rating.setRating(request.getRating());
        rating.setCategory(request.getCategory());
        rating.setDetailedComments(request.getDetailedComments());
        rating.setName(request.getName());
        rating.setEmail(request.getEmail());
        rating.setMetadata(request.getMetadata());
        rating.setUpdatedAt(LocalDateTime.now());

        OverallRating updatedRating = overallRatingRepository.save(rating);
        
        return convertToResponseDTO(updatedRating);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverallRatingResponseDTO> getOverallRatings(Long catalogId, int page, int size) {
        log.info("Getting overall ratings for catalogId: {}, page: {}, size: {}", catalogId, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<OverallRating> ratings = overallRatingRepository.findByCatalogIdOrderByCreatedAtDesc(catalogId, pageable);
        
        return ratings.getContent().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummaryDTO getRatingSummary(Long catalogId) {
        log.info("Getting rating summary for catalogId: {}", catalogId);
        
        Double averageRating = overallRatingRepository.findAverageRatingByCatalogId(catalogId);
        Long totalRatings = overallRatingRepository.countByCatalogId(catalogId);
        Long recentRatings = overallRatingRepository.findRecentRatingsCountByCatalogId(catalogId);
        
        // 평점별 분포 조회
        List<Object[]> ratingDistribution = overallRatingRepository.findRatingDistributionByCatalogId(catalogId);
        Map<Integer, Long> distributionMap = new HashMap<>();
        for (Object[] row : ratingDistribution) {
            distributionMap.put((Integer) row[0], (Long) row[1]);
        }
        
        // 카테고리별 평균 평점 조회
        List<Object[]> categoryRatings = overallRatingRepository.findCategoryAverageRatingsByCatalogId(catalogId);
        Map<String, Double> categoryMap = new HashMap<>();
        for (Object[] row : categoryRatings) {
            categoryMap.put((String) row[0], (Double) row[1]);
        }
        
        // 트렌드 계산 (간단한 로직)
        String trend = "STABLE";
        if (recentRatings > 0) {
            trend = "INCREASING";
        }
        
        // 신뢰도 점수 계산 (간단한 로직)
        Integer confidenceScore = Math.min(100, (int) (totalRatings * 10));
        
        RatingSummaryDTO summary = new RatingSummaryDTO();
        summary.setCatalogId(catalogId);
        summary.setAverageRating(averageRating != null ? averageRating : 0.0);
        summary.setTotalRatings(totalRatings);
        summary.setRatingDistribution(distributionMap);
        summary.setCategoryAverageRatings(categoryMap);
        summary.setRecentRatings(recentRatings);
        summary.setTrend(trend);
        summary.setConfidenceScore(confidenceScore);
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverallRatingResponseDTO> getUserRatings(String username, int page, int size) {
        log.info("Getting user ratings for username: {}, page: {}, size: {}", username, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<OverallRating> ratings = overallRatingRepository.findByUsernameOrderByCreatedAtDesc(username, pageable);
        
        return ratings.getContent().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getRatingStatistics(Long catalogId, String category) {
        log.info("Getting rating statistics for catalogId: {}, category: {}", catalogId, category);
        
        Map<String, Object> statistics = new HashMap<>();
        
        if (catalogId != null) {
            if (category != null) {
                // 특정 카탈로그의 카테고리별 통계
                Double averageRating = overallRatingRepository.findAverageRatingByCatalogIdAndCategory(catalogId, category);
                Long totalRatings = overallRatingRepository.countByCatalogIdAndCategory(catalogId, category);
                
                statistics.put("averageRating", averageRating != null ? averageRating : 0.0);
                statistics.put("totalRatings", totalRatings);
                statistics.put("category", category);
                statistics.put("catalogId", catalogId);
            } else {
                // 특정 카탈로그의 전체 통계
                Double averageRating = overallRatingRepository.findAverageRatingByCatalogId(catalogId);
                Long totalRatings = overallRatingRepository.countByCatalogId(catalogId);
                Long recentRatings = overallRatingRepository.findRecentRatingsCountByCatalogId(catalogId);
                
                statistics.put("averageRating", averageRating != null ? averageRating : 0.0);
                statistics.put("totalRatings", totalRatings);
                statistics.put("recentRatings", recentRatings);
                statistics.put("catalogId", catalogId);
            }
        } else {
            // 전체 시스템 통계 (모든 카탈로그)
            Double averageRating = overallRatingRepository.findOverallAverageRating();
            Long totalRatings = overallRatingRepository.countAllRatings();
            Long recentRatings = overallRatingRepository.findOverallRecentRatingsCount();
            
            statistics.put("averageRating", averageRating != null ? averageRating : 0.0);
            statistics.put("totalRatings", totalRatings);
            statistics.put("recentRatings", recentRatings);
            statistics.put("scope", "ALL");
        }
        
        return statistics;
    }

    @Override
    public void deleteOverallRating(Long ratingId, String username) {
        log.info("Deleting overall rating with id: {}, username: {}", ratingId, username);
        
        OverallRating rating = overallRatingRepository.findById(ratingId)
                .orElseThrow(() -> new RuntimeException("Rating not found with id: " + ratingId));
        
        // 사용자 권한 확인
        if (username != null && !username.equals(rating.getUsername())) {
            throw new RuntimeException("Unauthorized to delete this rating");
        }
        
        overallRatingRepository.delete(rating);
    }

    private OverallRatingResponseDTO convertToResponseDTO(OverallRating rating) {
        OverallRatingResponseDTO response = new OverallRatingResponseDTO();
        response.setRatingId(rating.getRatingId());
        response.setCatalogId(rating.getCatalogId());
        response.setRating(rating.getRating());
        response.setCategory(rating.getCategory());
        response.setDetailedComments(rating.getDetailedComments());
        response.setName(rating.getName());
        response.setEmail(rating.getEmail());
        response.setUsername(rating.getUsername());
        response.setMetadata(rating.getMetadata());
        response.setCreatedAt(rating.getCreatedAt());
        response.setUpdatedAt(rating.getUpdatedAt());
        return response;
    }
}
