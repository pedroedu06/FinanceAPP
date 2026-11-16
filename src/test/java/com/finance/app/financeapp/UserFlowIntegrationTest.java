package com.finance.app.financeapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.PasswordGuard;
import com.finance.app.financeapp.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercita a API de ponta a ponta (HTTP, seguranca, servicos, Flyway e banco).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserFlowIntegrationTest.FailingResource.class)
class UserFlowIntegrationTest {

	/** Rotas so de teste, para exercitar o tratamento de erros que nenhuma rota real provoca. */
	@TestComponent
	@RestController
	static class FailingResource {

		@GetMapping("/test/access-denied")
		void accessDenied() {
			throw new AccessDeniedException("negado");
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("detalhe interno que nao pode vazar");
		}

	}

	private static final String EMAIL = "maria@example.com";
	private static final String PASSWORD = "senha-da-maria-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	// ---------- cadastro ----------

	@Test
	void registerCreatesUserWithoutExposingPassword() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Maria"))
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.payDay").value(26))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(content().string(not(containsString(PASSWORD))));
	}

	@Test
	void registerStoresOnlyTheHash() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26).andExpect(status().isCreated());

		User saved = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26).andExpect(status().isCreated());

		register("Outra", "MARIA@Example.com", "outra-senha-123", 5).andExpect(status().isConflict());
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void registerValidatesFields() throws Exception {
		register("", "nao-e-email", "curta", 40)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists())
			.andExpect(jsonPath("$.errors.payDay").exists())
			.andExpect(content().string(not(containsString("curta"))));
		assertThat(userRepository.count()).isZero();
	}

	// ---------- login ----------

	@Test
	void loginReturnsBearerToken() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);

		login(" Maria@Example.com ", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void loginFailsTheSameWayForWrongPasswordAndUnknownEmail() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);

		String wrongPassword = login(EMAIL, "senha-errada")
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = login("ninguem@example.com", PASSWORD)
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();

		assertThat(wrongPassword).isEqualTo(unknownEmail);
	}

	@Test
	void loginLocksAccountAfterTooManyWrongPasswords() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);

		for (int i = 0; i < PasswordGuard.MAX_FAILED_ATTEMPTS; i++) {
			login(EMAIL, "senha-errada").andExpect(status().isUnauthorized());
		}

		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getLockedUntil()).isNotNull();
	}

	@Test
	void parallelWrongLoginsAllGetTheSameAnswerAndLockTheAccount() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);
		int requests = 40;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				statuses.add(pool.submit(() -> {
					start.await();
					return login(EMAIL, "senha-errada").andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> statusCode : statuses) {
				assertThat(statusCode.get(60, TimeUnit.SECONDS)).isEqualTo(401);
			}
		}
		finally {
			pool.shutdownNow();
		}

		User user = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(user.getFailedLoginAttempts()).isEqualTo(PasswordGuard.MAX_FAILED_ATTEMPTS);
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void parallelCorrectLoginsAllSucceed() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);
		int requests = 20;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				statuses.add(pool.submit(() -> {
					start.await();
					return login(EMAIL, PASSWORD).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> statusCode : statuses) {
				assertThat(statusCode.get(60, TimeUnit.SECONDS)).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getLockedUntil()).isNull();
	}

	@Test
	void successfulLoginResetsWrongAttempts() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26);
		login(EMAIL, "senha-errada").andExpect(status().isUnauthorized());
		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);

		login(EMAIL, PASSWORD).andExpect(status().isOk());
		assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getFailedLoginAttempts()).isZero();
	}

	// ---------- acesso ----------

	@Test
	void protectedRoutesRequireValidToken() throws Exception {
		mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
			.andExpect(status().isUnauthorized());

		String token = registerAndLogin();
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenMustBeUnexpiredAndIssuedByAndForThisApi() throws Exception {
		registerAndLogin();
		String userId = userRepository.findByEmail(EMAIL).orElseThrow().getId().toString();
		Instant inOneHour = Instant.now().plusSeconds(3600);

		authorized(get("/users/me"), token(userId, TokenService.ISSUER, TokenService.AUDIENCE, inOneHour))
			.andExpect(status().isOk());
		authorized(get("/users/me"),
				token(userId, TokenService.ISSUER, TokenService.AUDIENCE, Instant.now().minusSeconds(120)))
			.andExpect(status().isUnauthorized());
		authorized(get("/users/me"), token(userId, "outro-emissor", TokenService.AUDIENCE, inOneHour))
			.andExpect(status().isUnauthorized());
		authorized(get("/users/me"), token(userId, TokenService.ISSUER, "outra-api", inOneHour))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void onlyBasicHealthIsPublicAndNothingElseIsExposed() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());

		String token = registerAndLogin();
		authorized(get("/actuator/health"), token).andExpect(jsonPath("$.components").doesNotExist());
		authorized(get("/actuator/metrics"), token).andExpect(status().isNotFound());
		authorized(get("/users/me"), token).andExpect(header().string("X-Frame-Options", "DENY"));
	}

	@Test
	void eachUserSeesOnlyTheirOwnAccount() throws Exception {
		String mariaToken = registerAndLogin();
		register("João", "joao@example.com", "senha-do-joao-1", 5);
		String joaoToken = tokenOf(login("joao@example.com", "senha-do-joao-1"));

		authorized(get("/users/me"), mariaToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(content().string(not(containsString("joao@example.com"))));
		authorized(get("/users/me"), joaoToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("joao@example.com"));
	}

	@Test
	void securityErrorsFromControllersBecome403AndUnexpectedOnesACleanServerError() throws Exception {
		String token = registerAndLogin();

		authorized(get("/test/access-denied"), token).andExpect(status().isForbidden());
		authorized(get("/test/unexpected"), token)
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(not(containsString("detalhe interno"))));
	}

	// ---------- alteracao ----------

	@Test
	void updateChangesNameAndPayDay() throws Exception {
		String token = registerAndLogin();

		authorized(put("/users/me"), token, """
				{"name": "Maria Silva", "payDay": 5}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Silva"))
			.andExpect(jsonPath("$.payDay").value(5))
			.andExpect(jsonPath("$.email").value(EMAIL));

		authorized(put("/users/me"), token, """
				{"name": "Maria", "payDay": 0}""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void changeEmailRequiresCurrentPassword() throws Exception {
		String token = registerAndLogin();

		authorized(put("/users/me/email"), token, """
				{"newEmail": "nova@example.com", "currentPassword": "senha-errada"}""")
			.andExpect(status().isForbidden());
		assertThat(userRepository.existsByEmail(EMAIL)).isTrue();

		authorized(put("/users/me/email"), token, """
				{"newEmail": "Nova@Example.com", "currentPassword": "%s"}""".formatted(PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("nova@example.com"));

		// Trocar o e-mail derruba os tokens ja emitidos.
		authorized(get("/users/me"), token).andExpect(status().isUnauthorized());
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		login("nova@example.com", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void changeEmailRejectsEmailOfAnotherAccount() throws Exception {
		String token = registerAndLogin();
		register("João", "joao@example.com", "senha-do-joao-1", 5);

		authorized(put("/users/me/email"), token, """
				{"newEmail": "joao@example.com", "currentPassword": "%s"}""".formatted(PASSWORD))
			.andExpect(status().isConflict());
	}

	@Test
	void changePasswordRequiresCurrentPasswordAndInvalidatesOldToken() throws Exception {
		String oldToken = registerAndLogin();

		authorized(put("/users/me/password"), oldToken, """
				{"currentPassword": "senha-errada", "newPassword": "nova-senha-456"}""")
			.andExpect(status().isForbidden());
		authorized(put("/users/me/password"), oldToken, """
				{"currentPassword": "%s", "newPassword": "curta"}""".formatted(PASSWORD))
			.andExpect(status().isBadRequest());

		authorized(put("/users/me/password"), oldToken, """
				{"currentPassword": "%s", "newPassword": "nova-senha-456"}""".formatted(PASSWORD))
			.andExpect(status().isNoContent());

		authorized(get("/users/me"), oldToken).andExpect(status().isUnauthorized());
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		String newToken = tokenOf(login(EMAIL, "nova-senha-456"));
		authorized(get("/users/me"), newToken).andExpect(status().isOk());
	}

	@Test
	void wrongCurrentPasswordAlsoLocksTheAccount() throws Exception {
		String token = registerAndLogin();

		for (int i = 0; i < PasswordGuard.MAX_FAILED_ATTEMPTS; i++) {
			authorized(put("/users/me/password"), token, """
					{"currentPassword": "senha-errada", "newPassword": "nova-senha-456"}""")
				.andExpect(status().isForbidden());
		}

		authorized(put("/users/me/password"), token, """
				{"currentPassword": "%s", "newPassword": "nova-senha-456"}""".formatted(PASSWORD))
			.andExpect(status().isForbidden());
		assertThat(passwordEncoder.matches(PASSWORD, userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()))
			.isTrue();
	}

	// ---------- exclusao ----------

	@Test
	void deleteRequiresCurrentPasswordAndKillsTheToken() throws Exception {
		String token = registerAndLogin();

		authorized(post("/users/me/delete"), token, """
				{"currentPassword": "senha-errada"}""")
			.andExpect(status().isForbidden());
		assertThat(userRepository.existsByEmail(EMAIL)).isTrue();

		authorized(post("/users/me/delete"), token, """
				{"currentPassword": "%s"}""".formatted(PASSWORD))
			.andExpect(status().isNoContent());

		assertThat(userRepository.existsByEmail(EMAIL)).isFalse();
		authorized(get("/users/me"), token).andExpect(status().isUnauthorized());
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
	}

	// ---------- apoio ----------

	private ResultActions register(String name, String email, String password, int payDay) throws Exception {
		return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "%s", "email": "%s", "password": "%s", "payDay": %d}""".formatted(name, email, password, payDay)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(email, password)));
	}

	private String registerAndLogin() throws Exception {
		register("Maria", EMAIL, PASSWORD, 26).andExpect(status().isCreated());
		return tokenOf(login(EMAIL, PASSWORD));
	}

	private String token(String userId, String issuer, String audience, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.audience(List.of(audience))
			.subject(userId)
			.issuedAt(expiresAt.minusSeconds(3600))
			.expiresAt(expiresAt)
			.claim(TokenService.VERSION_CLAIM, 0)
			.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private String tokenOf(ResultActions loginResult) throws Exception {
		String body = loginResult.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private ResultActions authorized(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions authorized(MockHttpServletRequestBuilder request, String token, String json)
			throws Exception {
		return authorized(request.contentType(MediaType.APPLICATION_JSON).content(json), token);
	}

}
