package kr.co.mcmp.softwarecatalog.users.dto;

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

    public UserDTO(User entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
    }
}