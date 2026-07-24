package com.splitwisemoney.dto;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long acceptedGroupId;

    public AuthResponse(String token) {
        this.token = token;
    }

    public AuthResponse(String token, Long acceptedGroupId) {
        this.token = token;
        this.acceptedGroupId = acceptedGroupId;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public Long getAcceptedGroupId() { return acceptedGroupId; }
    public void setAcceptedGroupId(Long acceptedGroupId) { this.acceptedGroupId = acceptedGroupId; }
}
