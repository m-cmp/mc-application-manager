package kr.co.mcmp.softwarecatalog.rating.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.rating.entity.OverallRating;

@Repository
public interface OverallRatingRepository extends JpaRepository<OverallRating, Long> {

    /**
     * 카탈로그 ID로 평가 목록 조회
     */
    Page<OverallRating> findByCatalogIdOrderByCreatedAtDesc(Long catalogId, Pageable pageable);

    /**
     * 사용자명으로 평가 목록 조회
     */
    Page<OverallRating> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /**
     * 카탈로그 ID와 카테고리로 평가 목록 조회
     */
    Page<OverallRating> findByCatalogIdAndCategoryOrderByCreatedAtDesc(Long catalogId, String category, Pageable pageable);

    /**
     * 카탈로그 ID로 평균 평점 조회
     */
    @Query("SELECT AVG(r.rating) FROM OverallRating r WHERE r.catalogId = :catalogId")
    Double findAverageRatingByCatalogId(@Param("catalogId") Long catalogId);

    /**
     * 카탈로그 ID로 총 평가 수 조회
     */
    Long countByCatalogId(Long catalogId);

    /**
     * 카탈로그 ID와 카테고리로 평균 평점 조회
     */
    @Query("SELECT AVG(r.rating) FROM OverallRating r WHERE r.catalogId = :catalogId AND r.category = :category")
    Double findAverageRatingByCatalogIdAndCategory(@Param("catalogId") Long catalogId, @Param("category") String category);

    /**
     * 카탈로그 ID로 평점별 분포 조회
     */
    @Query("SELECT r.rating, COUNT(r) FROM OverallRating r WHERE r.catalogId = :catalogId GROUP BY r.rating")
    List<Object[]> findRatingDistributionByCatalogId(@Param("catalogId") Long catalogId);

    /**
     * 카탈로그 ID로 카테고리별 평균 평점 조회
     */
    @Query("SELECT r.category, AVG(r.rating) FROM OverallRating r WHERE r.catalogId = :catalogId GROUP BY r.category")
    List<Object[]> findCategoryAverageRatingsByCatalogId(@Param("catalogId") Long catalogId);

    /**
     * 최근 30일 평가 수 조회
     */
    @Query("SELECT COUNT(r) FROM OverallRating r WHERE r.catalogId = :catalogId AND r.createdAt >= CURRENT_DATE - 30")
    Long findRecentRatingsCountByCatalogId(@Param("catalogId") Long catalogId);

    /**
     * 사용자별 평가 수 조회
     */
    Long countByUsername(String username);

    /**
     * 카테고리별 평가 수 조회
     */
    Long countByCategory(String category);

    /**
     * 카탈로그 ID와 카테고리로 평가 수 조회
     */
    Long countByCatalogIdAndCategory(Long catalogId, String category);

    /**
     * 전체 시스템 평균 평점 조회 (모든 카탈로그)
     */
    @Query("SELECT AVG(r.rating) FROM OverallRating r")
    Double findOverallAverageRating();

    /**
     * 전체 시스템 총 평가 수 조회 (모든 카탈로그)
     */
    @Query("SELECT COUNT(r) FROM OverallRating r")
    Long countAllRatings();

    /**
     * 전체 시스템 최근 30일 평가 수 조회 (모든 카탈로그)
     */
    @Query("SELECT COUNT(r) FROM OverallRating r WHERE r.createdAt >= CURRENT_DATE - 30")
    Long findOverallRecentRatingsCount();
}
