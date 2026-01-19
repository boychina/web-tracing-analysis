package com.krielwus.webtracinganalysis.info;

public class Tokens {
    private final String accessToken;
    private final String refreshToken;
    private final Long refreshTokenId;

    public Tokens(String accessToken, String refreshToken, Long refreshTokenId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.refreshTokenId = refreshTokenId;
    }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Long getRefreshTokenId() { return refreshTokenId; }
}
