package com.finance.app.financeapp.services;

import com.finance.app.financeapp.dto.validation.ValidPasswordValidator;
import com.finance.app.financeapp.entities.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ponto unico de conferencia de senha. Conta as tentativas erradas e bloqueia a
 * conta por um tempo, tanto no login quanto nas acoes que pedem a senha atual.
 *
 * Altera os contadores no proprio {@link User}; quem chama precisa estar em uma
 * transacao que nao faca rollback quando a senha estiver errada.
 */
@Component
public class PasswordGuard {

	public static final int MAX_FAILED_ATTEMPTS = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final String dummyHash;

	public PasswordGuard(PasswordEncoder passwordEncoder, Clock clock) {
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("senha-que-ninguem-usa");
	}

	public boolean verify(User user, String rawPassword) {
		Instant now = clock.instant();
		if (user.getLockedUntil() != null) {
			if (now.isBefore(user.getLockedUntil())) {
				simulateVerification(rawPassword);
				return false;
			}
			user.setLockedUntil(null);
		}

		if (matches(rawPassword, user.getPasswordHash())) {
			user.setFailedLoginAttempts(0);
			return true;
		}

		int attempts = user.getFailedLoginAttempts() + 1;
		if (attempts >= MAX_FAILED_ATTEMPTS) {
			user.setLockedUntil(now.plus(LOCK_DURATION));
			attempts = 0;
		}
		user.setFailedLoginAttempts(attempts);
		return false;
	}

	/**
	 * Gasta o mesmo tempo de uma conferencia real, para que a resposta nao revele
	 * pelo tempo se o e-mail existe.
	 */
	public void simulateVerification(String rawPassword) {
		matches(rawPassword, dummyHash);
	}

	private boolean matches(String rawPassword, String hash) {
		if (rawPassword == null
				|| rawPassword.getBytes(StandardCharsets.UTF_8).length > ValidPasswordValidator.MAX_BYTES) {
			return false;
		}
		return passwordEncoder.matches(rawPassword, hash);
	}

}
