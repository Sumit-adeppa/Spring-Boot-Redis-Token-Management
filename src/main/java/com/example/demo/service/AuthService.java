package com.example.demo.service;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AuthenticationResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.TokenPair;
import com.example.demo.exception.ApiException;
import com.example.demo.model.User;
import com.example.demo.repository.TokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CustomUserService customUserService;

    @Transactional
    public AuthenticationResponse registerNewUser(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (username.length() < 3 || username.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username must contain 3 to 80 non-space characters");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes when BCrypt is used");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already registered");
        }

        userRepository.saveAndFlush(
                new User(username, passwordEncoder.encode(request.getPassword())));
        return authenticateUser(username, request.getPassword());
    }

    public AuthenticationResponse login(LoginRequest request) {
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes when BCrypt is used");
        }
        return authenticateUser(request.getUsername().trim(), request.getPassword());
    }

    private AuthenticationResponse authenticateUser(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        UserDetails user = (UserDetails) authentication.getPrincipal();
        TokenPair pair = jwtTokenProvider.generateTokenPair(authentication);

        // Atomic replacement: any older pair stops matching the active Redis values.
        tokenRepository.storeTokens(user.getUsername(), pair.getAccessToken(), pair.getRefreshToken());

        return new AuthenticationResponse(
                pair.getAccessToken(), pair.getRefreshToken(), user.getAuthorities());
    }

    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        tokenRepository.removeAllTokens(user.getUsername());
        SecurityContextHolder.clearContext();
    }

    public AuthenticationResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)
                || tokenRepository.isRefreshTokenBlackListed(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        if (!refreshToken.equals(tokenRepository.getRefreshToken(username))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is no longer active");
        }

        UserDetails user = customUserService.loadUserByUsername(username);
        String newAccess = jwtTokenProvider.generateAccessToken(username);
        String newRefresh = jwtTokenProvider.generateRefreshToken(username);

        if (!tokenRepository.rotateTokens(username, refreshToken, newAccess, newRefresh)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has already been used");
        }

        return new AuthenticationResponse(newAccess, newRefresh, user.getAuthorities());
    }
}
