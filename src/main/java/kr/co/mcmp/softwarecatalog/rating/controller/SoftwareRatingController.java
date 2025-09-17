package kr.co.mcmp.softwarecatalog.rating.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.mcmp.response.ResponseWrapper;
import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingRequestDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.OverallRatingResponseDTO;
import kr.co.mcmp.softwarecatalog.rating.dto.RatingSummaryDTO;
import kr.co.mcmp.softwarecatalog.rating.service.SoftwareRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Software Rating Management", description = "소프트웨어 평가 및 평점 관리 API")
@RestController
@Slf4j
@RequestMapping("/catalog/rating")
@RequiredArgsConstructor
public class SoftwareRatingController {

    private final SoftwareRatingService softwareRatingService;

    @Operation(summary = "소프트웨어 전체 평가 등록", description = "소프트웨어에 대한 전체 평가를 등록합니다.")
    @PostMapping("/overall")
    public ResponseEntity<ResponseWrapper<OverallRatingResponseDTO>> createOverallRating(
            @RequestBody OverallRatingRequestDTO request,
            @RequestParam(required = false) String username) {
        OverallRatingResponseDTO response = softwareRatingService.createOverallRating(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(response));
    }

    @Operation(summary = "소프트웨어 전체 평가 수정", description = "기존 전체 평가를 수정합니다.")
    @PutMapping("/overall/{ratingId}")
    public ResponseEntity<ResponseWrapper<OverallRatingResponseDTO>> updateOverallRating(
            @PathVariable Long ratingId,
            @RequestBody OverallRatingRequestDTO request,
            @RequestParam(required = false) String username) {
        OverallRatingResponseDTO response = softwareRatingService.updateOverallRating(ratingId, request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(response));
    }

    @Operation(summary = "소프트웨어 전체 평가 조회", description = "특정 소프트웨어의 전체 평가를 조회합니다.")
    @GetMapping("/overall/{catalogId}")
    public ResponseEntity<ResponseWrapper<List<OverallRatingResponseDTO>>> getOverallRatings(
            @PathVariable Long catalogId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<OverallRatingResponseDTO> ratings = softwareRatingService.getOverallRatings(catalogId, page, size);
        return ResponseEntity.ok(new ResponseWrapper<>(ratings));
    }

    @Operation(summary = "소프트웨어 평점 요약 조회", description = "소프트웨어의 평점 요약 정보를 조회합니다.")
    @GetMapping("/summary/{catalogId}")
    public ResponseEntity<ResponseWrapper<RatingSummaryDTO>> getRatingSummary(
            @PathVariable Long catalogId) {
        RatingSummaryDTO summary = softwareRatingService.getRatingSummary(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(summary));
    }

    @Operation(summary = "사용자별 평가 조회", description = "특정 사용자의 평가 목록을 조회합니다.")
    @GetMapping("/user/{username}")
    public ResponseEntity<ResponseWrapper<List<OverallRatingResponseDTO>>> getUserRatings(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<OverallRatingResponseDTO> ratings = softwareRatingService.getUserRatings(username, page, size);
        return ResponseEntity.ok(new ResponseWrapper<>(ratings));
    }

    @Operation(summary = "평가 통계 조회", description = "평가 통계 정보를 조회합니다.")
    @GetMapping("/statistics")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getRatingStatistics(
            @RequestParam(required = false) Long catalogId,
            @RequestParam(required = false) String category) {
        Map<String, Object> statistics = softwareRatingService.getRatingStatistics(catalogId, category);
        return ResponseEntity.ok(new ResponseWrapper<>(statistics));
    }

    @Operation(summary = "평가 삭제", description = "사용자가 작성한 평가를 삭제합니다.")
    @PutMapping("/overall/{ratingId}/delete")
    public ResponseEntity<ResponseWrapper<Void>> deleteOverallRating(
            @PathVariable Long ratingId,
            @RequestParam String username) {
        softwareRatingService.deleteOverallRating(ratingId, username);
        return ResponseEntity.ok(new ResponseWrapper<>());
    }
}
