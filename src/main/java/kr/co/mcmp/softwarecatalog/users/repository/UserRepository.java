package kr.co.mcmp.softwarecatalog.users.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.co.mcmp.softwarecatalog.users.Entity.User;

public interface UserRepository extends JpaRepository<User, Long>  {
    Optional<User> findByUsername(String username);
}
