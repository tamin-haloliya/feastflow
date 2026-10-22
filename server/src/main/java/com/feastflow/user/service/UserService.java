package com.feastflow.user.service;

import com.feastflow.user.dto.UserResponse;
import com.feastflow.user.entity.UserEntity;
import com.feastflow.user.exception.UserNotFoundException;
import com.feastflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        if (!user.isEnabled()) {
            throw new AccessDeniedException("Account disabled");
        }

        return UserResponse.of(user);
    }
}