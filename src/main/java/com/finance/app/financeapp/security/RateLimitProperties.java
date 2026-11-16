package com.finance.app.financeapp.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limites por IP das rotas publicas. */
@ConfigurationProperties("app.security.rate-limit")
@Validated
public record RateLimitProperties(@Min(1) int loginPerMinute, @Min(1) int registerPerHour) {
}
