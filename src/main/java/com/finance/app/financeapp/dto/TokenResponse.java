package com.finance.app.financeapp.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
