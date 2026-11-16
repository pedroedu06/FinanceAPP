package com.finance.app.financeapp.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * O segredo assina os tokens com HS256, que exige pelo menos 256 bits (32 bytes).
 * A aplicacao nao sobe sem ele.
 */
@ConfigurationProperties("app.security.jwt")
@Validated
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "app.security.jwt.secret (JWT_SECRET) precisa ter pelo menos 32 caracteres") String secret,
		@NotNull Duration expiration) {
}
