package kr.co.mcmp.softwarecatalog.users.Entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "USERS")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 사용자의 고유 식별자

    @Column(nullable = false, unique = true)
    private String username; // 사용자 이름 (로그인 ID)

    // @Column(name = "phone_number")
    // private String phoneNumber; // 전화번호

    // @Enumerated(EnumType.STRING)
    // @Column(nullable = false)
    // private UserRole role; // 사용자 역할 (예: ADMIN, USER, MANAGER 등)

    // @Column(name = "is_active", nullable = false)
    // private boolean isActive; // 계정 활성화 상태

    // @Column(name = "last_login")
    // private LocalDateTime lastLogin; // 마지막 로그인 시간

    // @Column(name = "created_at", nullable = false)
    // private LocalDateTime createdAt; // 계정 생성 시간

    // @Column(name = "updated_at")
    // private LocalDateTime updatedAt; // 계정 정보 최종 수정 시간

    // @PrePersist
    // protected void onCreate() {
    //     createdAt = LocalDateTime.now();
    // }

    // @PreUpdate
    // protected void onUpdate() {
    //     updatedAt = LocalDateTime.now();
    // }

}

// public enum UserRole {
//     ADMIN, USER, MANAGER
// }