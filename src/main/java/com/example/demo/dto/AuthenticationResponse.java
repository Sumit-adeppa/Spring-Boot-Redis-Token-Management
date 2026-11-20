package com.example.demo.dto;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public class AuthenticationResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Collection<? extends GrantedAuthority> authorities;

    public AuthenticationResponse() {}
    public AuthenticationResponse(String accessToken, String refreshToken,
                                  Collection<? extends GrantedAuthority> authorities) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.authorities = authorities;
    }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
}
