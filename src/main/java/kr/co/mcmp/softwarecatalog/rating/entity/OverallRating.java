package kr.co.mcmp.softwarecatalog.rating.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "overall_rating")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverallRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rating_id")
    private Long ratingId;

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "detailed_comments", length = 1000)
    private String detailedComments;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "metadata", length = 2000)
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
