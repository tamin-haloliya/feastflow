package com.feastflow.user.dto;

import com.feastflow.user.entity.PlatformRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String name;
    private String email;
    private PlatformRole role;
    private Instant createdAt;

    public static UserResponse of(com.feastflow.user.entity.UserEntity user) {
        return UserResponse.builder()
                .name(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}