package com.pm.authservice.service;

import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.model.User;
import com.pm.authservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserService userService;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtUtil jwtUtil;

  @InjectMocks
  private AuthService authService;

  private User testUser;
  private LoginRequestDTO loginRequest;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setEmail("test@example.com");
    testUser.setPassword("$2b$12$hashedPassword");
    testUser.setRole("ADMIN");

    loginRequest = new LoginRequestDTO();
    loginRequest.setEmail("test@example.com");
    loginRequest.setPassword("password123");
  }

  @Test
  void authenticate_ShouldReturnTokenForValidCredentials() {
    // Given
    when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
    when(jwtUtil.generateToken("test@example.com", "ADMIN")).thenReturn("test-token");

    // When
    Optional<String> result = authService.authenticate(loginRequest);

    // Then
    assertTrue(result.isPresent());
    assertEquals("test-token", result.get());
    verify(jwtUtil).generateToken("test@example.com", "ADMIN");
  }

  @Test
  void authenticate_ShouldReturnEmptyForInvalidPassword() {
    // Given
    when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("wrongpassword", testUser.getPassword())).thenReturn(false);

    loginRequest.setPassword("wrongpassword");

    // When
    Optional<String> result = authService.authenticate(loginRequest);

    // Then
    assertFalse(result.isPresent());
    verify(jwtUtil, never()).generateToken(anyString(), anyString());
  }

  @Test
  void authenticate_ShouldReturnEmptyForNonExistentUser() {
    // Given
    when(userService.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

    loginRequest.setEmail("nonexistent@example.com");

    // When
    Optional<String> result = authService.authenticate(loginRequest);

    // Then
    assertFalse(result.isPresent());
    verify(jwtUtil, never()).generateToken(anyString(), anyString());
  }

  @Test
  void validateToken_ShouldReturnTrueForValidToken() {
    // Given
    String token = "valid-token";
    doNothing().when(jwtUtil).validateToken(token);

    // When
    boolean result = authService.validateToken(token);

    // Then
    assertTrue(result);
    verify(jwtUtil).validateToken(token);
  }

  @Test
  void validateToken_ShouldReturnFalseForInvalidToken() {
    // Given
    String token = "invalid-token";
    doThrow(new io.jsonwebtoken.JwtException("Invalid token")).when(jwtUtil).validateToken(token);

    // When
    boolean result = authService.validateToken(token);

    // Then
    assertFalse(result);
    verify(jwtUtil).validateToken(token);
  }
}


