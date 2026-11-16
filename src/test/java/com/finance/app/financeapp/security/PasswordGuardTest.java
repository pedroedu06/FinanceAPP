package com.finance.app.financeapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Roda contra o banco: o controle de tentativas e feito por UPDATEs atomicos, e
 * e isso que precisa ser provado, inclusive sob requisicoes paralelas.
 */
@SpringBootTest
@ActiveProfiles("test")
class PasswordGuardTest {

	private static final String PASSWORD = "senha-correta-1";

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

		@Bean
		@Primary
		CountingPasswordEncoder countingPasswordEncoder() {
			return new CountingPasswordEncoder();
		}

	}

	/** BCrypt de custo baixo que conta quantas vezes cada hash foi conferido. */
	static class CountingPasswordEncoder implements PasswordEncoder {

		private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);
		private final ConcurrentHashMap<String, AtomicInteger> checks = new ConcurrentHashMap<>();

		@Override
		public String encode(CharSequence rawPassword) {
			return delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			checks.computeIfAbsent(encodedPassword, key -> new AtomicInteger()).incrementAndGet();
			return delegate.matches(rawPassword, encodedPassword);
		}

		int checksOf(String encodedPassword) {
			AtomicInteger count = checks.get(encodedPassword);
			return count == null ? 0 : count.get();
		}

	}

	@Autowired
	private PasswordGuard guard;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CountingPasswordEncoder passwordEncoder;

	@Autowired
	private MutableClock clock;

	private UUID userId;
	private String passwordHash;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		passwordHash = passwordEncoder.encode(PASSWORD);
		userId = userRepository.saveAndFlush(new User("Maria", "guard@example.com", passwordHash, 26)).getId();
	}

	@AfterEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void acceptsCorrectPassword() {
		assertThat(guard.verify(userId, PASSWORD)).isTrue();
		assertThat(user().getFailedLoginAttempts()).isZero();
	}

	@Test
	void countsWrongAttemptsAndResetsOnSuccess() {
		assertThat(guard.verify(userId, "errada")).isFalse();
		assertThat(guard.verify(userId, "errada")).isFalse();
		assertThat(user().getFailedLoginAttempts()).isEqualTo(2);

		assertThat(guard.verify(userId, PASSWORD)).isTrue();
		assertThat(user().getFailedLoginAttempts()).isZero();
	}

	@Test
	void locksAfterMaxAttemptsEvenForCorrectPassword() {
		failUntilLocked();

		assertThat(user().getLockedUntil()).isEqualTo(clock.instant().plus(PasswordGuard.LOCK_DURATION));
		assertThat(guard.verify(userId, PASSWORD)).isFalse();
	}

	@Test
	void unlocksWhenLockExpires() {
		failUntilLocked();

		clock.advance(PasswordGuard.LOCK_DURATION.minusSeconds(1));
		assertThat(guard.verify(userId, PASSWORD)).isFalse();

		clock.advance(Duration.ofSeconds(2));
		assertThat(guard.verify(userId, PASSWORD)).isTrue();
		assertThat(user().getLockedUntil()).isNull();
		assertThat(user().getFailedLoginAttempts()).isZero();
	}

	@Test
	void rejectsPasswordLongerThanBcryptLimitWithoutFailing() {
		assertThat(guard.verify(userId, "x".repeat(500))).isFalse();
	}

	@Test
	void rejectsUnknownAccount() {
		assertThat(guard.verify(UUID.randomUUID(), PASSWORD)).isFalse();
	}

	@Test
	void savingTheEntityDoesNotUndoTheLock() {
		User stale = user();
		failUntilLocked();

		stale.setName("Maria Silva");
		userRepository.saveAndFlush(stale);

		assertThat(user().getName()).isEqualTo("Maria Silva");
		assertThat(user().getLockedUntil()).isNotNull();
		assertThat(guard.verify(userId, PASSWORD)).isFalse();
	}

	@Test
	void parallelAttemptsCannotTestMorePasswordsThanTheLimit() throws Exception {
		int requests = 40;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return guard.verify(userId, "errada");
				}));
			}
			start.countDown();
			for (Future<Boolean> result : results) {
				assertThat(result.get(60, TimeUnit.SECONDS)).isFalse();
			}
		}
		finally {
			pool.shutdownNow();
		}

		// So as tentativas reservadas chegam a conferir a senha de verdade.
		assertThat(passwordEncoder.checksOf(passwordHash)).isEqualTo(PasswordGuard.MAX_FAILED_ATTEMPTS);
		assertThat(user().getFailedLoginAttempts()).isEqualTo(PasswordGuard.MAX_FAILED_ATTEMPTS);
		assertThat(user().getLockedUntil()).isNotNull();
		assertThat(guard.verify(userId, PASSWORD)).isFalse();
	}

	@Test
	void parallelCorrectPasswordsAreAllAccepted() throws Exception {
		int requests = 20;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return guard.verify(userId, PASSWORD);
				}));
			}
			start.countDown();
			for (Future<Boolean> result : results) {
				assertThat(result.get(60, TimeUnit.SECONDS)).isTrue();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(user().getLockedUntil()).isNull();
		assertThat(user().getFailedLoginAttempts()).isZero();
	}

	private void failUntilLocked() {
		for (int i = 0; i < PasswordGuard.MAX_FAILED_ATTEMPTS; i++) {
			assertThat(guard.verify(userId, "errada")).isFalse();
		}
	}

	private User user() {
		return userRepository.findById(userId).orElseThrow();
	}

}
