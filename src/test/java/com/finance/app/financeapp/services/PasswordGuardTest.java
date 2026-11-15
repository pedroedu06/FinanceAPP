package com.finance.app.financeapp.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.finance.app.financeapp.entities.User;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordGuardTest {

	private static final String PASSWORD = "senha-correta-1";

	// Custo 4 so para o teste rodar rapido.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final MutableClock clock = new MutableClock();
	private final PasswordGuard guard = new PasswordGuard(passwordEncoder, clock);

	private User user;

	@BeforeEach
	void setUp() {
		user = new User("Maria", "maria@example.com", passwordEncoder.encode(PASSWORD), 26);
	}

	@Test
	void acceptsCorrectPassword() {
		assertThat(guard.verify(user, PASSWORD)).isTrue();
		assertThat(user.getFailedLoginAttempts()).isZero();
	}

	@Test
	void countsWrongAttemptsAndResetsOnSuccess() {
		assertThat(guard.verify(user, "errada")).isFalse();
		assertThat(guard.verify(user, "errada")).isFalse();
		assertThat(user.getFailedLoginAttempts()).isEqualTo(2);

		assertThat(guard.verify(user, PASSWORD)).isTrue();
		assertThat(user.getFailedLoginAttempts()).isZero();
	}

	@Test
	void locksAfterMaxAttemptsEvenForCorrectPassword() {
		failUntilLocked();

		assertThat(user.getLockedUntil()).isEqualTo(clock.instant().plus(PasswordGuard.LOCK_DURATION));
		assertThat(guard.verify(user, PASSWORD)).isFalse();
	}

	@Test
	void unlocksWhenLockExpires() {
		failUntilLocked();

		clock.advance(PasswordGuard.LOCK_DURATION.minusSeconds(1));
		assertThat(guard.verify(user, PASSWORD)).isFalse();

		clock.advance(Duration.ofSeconds(2));
		assertThat(guard.verify(user, PASSWORD)).isTrue();
		assertThat(user.getLockedUntil()).isNull();
	}

	@Test
	void rejectsPasswordLongerThanBcryptLimitWithoutFailing() {
		assertThat(guard.verify(user, "x".repeat(500))).isFalse();
	}

	private void failUntilLocked() {
		for (int i = 0; i < PasswordGuard.MAX_FAILED_ATTEMPTS; i++) {
			assertThat(guard.verify(user, "errada")).isFalse();
		}
	}

}
