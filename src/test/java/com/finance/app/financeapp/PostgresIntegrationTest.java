package com.finance.app.financeapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.PasswordGuard;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mesmo fluxo principal, mas contra um PostgreSQL real (Testcontainers): prova
 * que as migrations e os UPDATEs nativos do controle de tentativas funcionam no
 * banco de producao, e nao so no H2. E pulado quando nao ha Docker; no CI roda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFinanceappApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

	private static final String EMAIL = "postgres@example.com";
	private static final String PASSWORD = "senha-no-postgres-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void registerLoginAndReadAccount() throws Exception {
		register().andExpect(status().isCreated());

		String body = login(PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void duplicateEmailIsRejectedByTheDatabaseConstraint() throws Exception {
		register().andExpect(status().isCreated());
		register().andExpect(status().isConflict());
	}

	@Test
	void wrongPasswordsLockTheAccount() throws Exception {
		register().andExpect(status().isCreated());

		for (int i = 0; i < PasswordGuard.MAX_FAILED_ATTEMPTS; i++) {
			login("senha-errada").andExpect(status().isUnauthorized());
		}

		login(PASSWORD).andExpect(status().isUnauthorized());
		User user = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(user.getFailedLoginAttempts()).isEqualTo(PasswordGuard.MAX_FAILED_ATTEMPTS);
		assertThat(user.getLockedUntil()).isNotNull();
	}

	@Test
	void parallelWrongLoginsNeverExceedTheAttemptLimit() throws Exception {
		register().andExpect(status().isCreated());

		assertThat(parallelLogins(40, "senha-errada")).containsOnly(401);

		User user = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(user.getFailedLoginAttempts()).isEqualTo(PasswordGuard.MAX_FAILED_ATTEMPTS);
		assertThat(user.getLockedUntil()).isNotNull();
		login(PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void parallelCorrectLoginsAllSucceed() throws Exception {
		register().andExpect(status().isCreated());

		assertThat(parallelLogins(20, PASSWORD)).containsOnly(200);

		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getLockedUntil()).isNull();
	}

	private List<Integer> parallelLogins(int requests, String password) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> pending = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				pending.add(pool.submit(() -> {
					start.await();
					return login(password).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : pending) {
				statuses.add(result.get(60, TimeUnit.SECONDS));
			}
			return statuses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private ResultActions register() throws Exception {
		return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Postgres", "email": "%s", "password": "%s", "payDay": 15}""".formatted(EMAIL, PASSWORD)));
	}

	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(EMAIL, password)));
	}

}
