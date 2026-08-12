package com.neganote.bankapi.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String tokenType; // "Bearer"
    private long expiresInMs;
    private String username;
    private String role;
}
