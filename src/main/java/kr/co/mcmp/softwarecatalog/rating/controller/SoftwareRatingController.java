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

@Tag(name = "Software Rating Management", description = "Software rating and evaluation management API")
@RestController
@Slf4j
@RequestMapping("/catalog/rating")
@RequiredArgsConstructor
public class SoftwareRatingController {

    private final SoftwareRatingService softwareRatingService;

    @Operation(summary = "Create Overall Software Rating", description = "Register overall evaluation for software.")
    @PostMapping("/overall")
    public ResponseEntity<ResponseWrapper<OverallRatingResponseDTO>> createOverallRating(
            @RequestBody OverallRatingRequestDTO request,
            @RequestParam(required = false) String username) {
        OverallRatingResponseDTO response = softwareRatingService.createOverallRating(request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(response));
    }

    @Operation(summary = "Update Overall Software Rating", description = "Update existing overall evaluation.")
    @PutMapping("/overall/{ratingId}")
    public ResponseEntity<ResponseWrapper<OverallRatingResponseDTO>> updateOverallRating(
            @PathVariable Long ratingId,
            @RequestBody OverallRatingRequestDTO request,
            @RequestParam(required = false) String username) {
        OverallRatingResponseDTO response = softwareRatingService.updateOverallRating(ratingId, request, username);
        return ResponseEntity.ok(new ResponseWrapper<>(response));
    }

    @Operation(summary = "Get Overall Software Ratings", description = "Retrieve overall evaluations for specific software.")
    @GetMapping("/overall/{catalogId}")
    public ResponseEntity<ResponseWrapper<List<OverallRatingResponseDTO>>> getOverallRatings(
            @PathVariable Long catalogId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<OverallRatingResponseDTO> ratings = softwareRatingService.getOverallRatings(catalogId, page, size);
        return ResponseEntity.ok(new ResponseWrapper<>(ratings));
    }

    @Operation(summary = "Get Software Rating Summary", description = "Retrieve software rating summary information.")
    @GetMapping("/summary/{catalogId}")
    public ResponseEntity<ResponseWrapper<RatingSummaryDTO>> getRatingSummary(
            @PathVariable Long catalogId) {
        RatingSummaryDTO summary = softwareRatingService.getRatingSummary(catalogId);
        return ResponseEntity.ok(new ResponseWrapper<>(summary));
    }

    @Operation(summary = "Get User Ratings", description = "Retrieve rating list for specific user.")
    @GetMapping("/user/{username}")
    public ResponseEntity<ResponseWrapper<List<OverallRatingResponseDTO>>> getUserRatings(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<OverallRatingResponseDTO> ratings = softwareRatingService.getUserRatings(username, page, size);
        return ResponseEntity.ok(new ResponseWrapper<>(ratings));
    }

    @Operation(summary = "Get Rating Statistics", description = "Retrieve rating statistics information.")
    @GetMapping("/statistics")
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> getRatingStatistics(
            @RequestParam(required = false) Long catalogId,
            @RequestParam(required = false) String category) {
        Map<String, Object> statistics = softwareRatingService.getRatingStatistics(catalogId, category);
        return ResponseEntity.ok(new ResponseWrapper<>(statistics));
    }

    @Operation(summary = "Delete Rating", description = "Delete evaluation written by user.")
    @PutMapping("/overall/{ratingId}/delete")
    public ResponseEntity<ResponseWrapper<Void>> deleteOverallRating(
            @PathVariable Long ratingId,
            @RequestParam String username) {
        softwareRatingService.deleteOverallRating(ratingId, username);
        return ResponseEntity.ok(new ResponseWrapper<>());
    }
}
