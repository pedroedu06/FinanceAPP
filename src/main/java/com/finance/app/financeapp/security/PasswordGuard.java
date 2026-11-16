package com.finance.app.financeapp.security;

import com.finance.app.financeapp.repositories.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ponto unico de conferencia de senha, usado no login e nas acoes que pedem a
 * senha atual. Limita as tentativas erradas e bloqueia a conta por um tempo.
 *
 * Cada tentativa e reservada no banco, com um UPDATE atomico, antes de a senha
 * ser avaliada. Por isso requisicoes paralelas nao conseguem testar mais senhas
 * do que o limite, e o contador nao depende da transacao de quem chama.
 */
@Component
public class PasswordGuard {

	public static final int MAX_FAILED_ATTEMPTS = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	// Um bloqueio recem-criado pode ser so o efeito de varias requisicoes simultaneas que
	// ainda estao conferindo a senha. Se alguma acertar, o bloqueio some; entao a requisicao
	// barrada espera um pouco e tenta reservar de novo, em vez de recusar um usuario legitimo.
	// A espera nao da tentativas extras: cada conferencia continua exigindo uma reserva.
	static final Duration FRESH_LOCK = Duration.ofSeconds(2);
	static final int MAX_RETRIES = 10;
	static final long RETRY_PAUSE_MILLIS = 50;

	private static final Logger audit = LoggerFactory.getLogger("security.audit");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final String dummyHash;

	public PasswordGuard(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("senha-que-ninguem-usa");
	}

	public boolean verify(UUID userId, String rawPassword) {
		boolean claimed = claimAttempt(userId);
		for (int retry = 0; !claimed && retry < MAX_RETRIES; retry++) {
			Instant lockedUntil = userRepository.findLockedUntilById(userId).orElse(null);
			if (lockedUntil != null) {
				// Bloqueio antigo e bloqueio de verdade; so o recem-criado vale a espera.
				if (!isFresh(lockedUntil) || !pause()) {
					break;
				}
			}
			// Sem bloqueio (alguem acabou de acertar a senha e liberou a conta): tenta de novo ja.
			claimed = claimAttempt(userId);
		}
		if (!claimed) {
			simulateVerification(rawPassword);
			audit.warn("Tentativa de senha recusada: conta {} bloqueada", userId);
			return false;
		}

		String hash = userRepository.findPasswordHashById(userId).orElse(null);
		if (hash == null || !matches(rawPassword, hash)) {
			audit.warn("Senha incorreta para a conta {}", userId);
			return false;
		}
		userRepository.resetPasswordAttempts(userId);
		return true;
	}

	/**
	 * Gasta o mesmo tempo de uma conferencia real, para que a resposta nao revele
	 * pelo tempo se o e-mail existe.
	 */
	public void simulateVerification(String rawPassword) {
		matches(rawPassword, dummyHash);
	}

	private boolean claimAttempt(UUID userId) {
		Instant now = clock.instant();
		userRepository.clearExpiredLock(userId, now);
		return userRepository.claimPasswordAttempt(userId, MAX_FAILED_ATTEMPTS, now.plus(LOCK_DURATION)) > 0;
	}

	private boolean isFresh(Instant lockedUntil) {
		return lockedUntil.minus(LOCK_DURATION).isAfter(clock.instant().minus(FRESH_LOCK));
	}

	private static boolean pause() {
		try {
			Thread.sleep(RETRY_PAUSE_MILLIS);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private boolean matches(String rawPassword, String hash) {
		if (rawPassword == null
				|| rawPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
			return false;
		}
		return passwordEncoder.matches(rawPassword, hash);
	}

}
