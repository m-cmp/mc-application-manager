package kr.co.mcmp.softwarecatalog.users.service;

import java.util.Optional;

import javax.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.users.Entity.User;
import kr.co.mcmp.softwarecatalog.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    
 private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            return userOpt.orElseThrow(() -> {
                return new EntityNotFoundException("User not found with username: " + username);
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Error occurred while fetching user", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error occurred while finding user", e);
        }
    }

    @Transactional
    public User createUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        try {
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                throw new IllegalArgumentException("User already exists with username: " + user.getUsername());
            }
            return userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error occurred while creating user", e);
        }
    }

    @Transactional
    public User updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID cannot be null");
        }

        try {
            Optional<User> existingUserOpt = userRepository.findById(user.getId());
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                existingUser.setUsername(user.getUsername());
                return userRepository.save(existingUser);
            } else {
                throw new EntityNotFoundException("User not found with ID: " + user.getId());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error occurred while updating user", e);
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        try {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
            } else {
                throw new EntityNotFoundException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error occurred while deleting user", e);
        }
    }

}
