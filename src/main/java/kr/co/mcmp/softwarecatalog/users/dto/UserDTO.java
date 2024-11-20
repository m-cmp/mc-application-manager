package kr.co.mcmp.softwarecatalog.users.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.softwarecatalog.users.Entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    // private String email;
    // private String firstName;
    // private String lastName;
    // private String phoneNumber;
    // private UserRole role;
    // private boolean isActive;
    // private LocalDateTime lastLogin;
    // private LocalDateTime createdAt;
    // private LocalDateTime updatedAt;

    public UserDTO(User entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
        // this.email = entity.getEmail();
        // this.firstName = entity.getFirstName();
        // this.lastName = entity.getLastName();
        // this.phoneNumber = entity.getPhoneNumber();
        // this.role = entity.getRole();
        // this.isActive = entity.isActive();
        // this.lastLogin = entity.getLastLogin();
        // this.createdAt = entity.getCreatedAt();
        // this.updatedAt = entity.getUpdatedAt();
    }
}