package com.finance.app.financeapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.app.financeapp.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = { "app.security.rate-limit.login-per-minute=3",
		"app.security.rate-limit.register-per-hour=2" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void loginIsLimitedPerIp() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(login("10.0.0.1")).andExpect(status().isUnauthorized());
		}

		mockMvc.perform(login("10.0.0.1"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		// Outro IP nao e afetado.
		mockMvc.perform(login("10.0.0.2")).andExpect(status().isUnauthorized());
	}

	@Test
	void registrationIsLimitedPerIp() throws Exception {
		mockMvc.perform(register("10.0.0.3", "um@example.com")).andExpect(status().isCreated());
		mockMvc.perform(register("10.0.0.3", "dois@example.com")).andExpect(status().isCreated());

		mockMvc.perform(register("10.0.0.3", "tres@example.com")).andExpect(status().isTooManyRequests());
		mockMvc.perform(register("10.0.0.4", "tres@example.com")).andExpect(status().isCreated());
	}

	private static MockHttpServletRequestBuilder login(String ip) {
		return from(ip, post("/auth/login")).content("""
				{"email": "ninguem@example.com", "password": "senha-qualquer"}""");
	}

	private static MockHttpServletRequestBuilder register(String ip, String email) {
		return from(ip, post("/users")).content("""
				{"name": "Teste", "email": "%s", "password": "senha-de-teste-1", "payDay": 5}""".formatted(email));
	}

	private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder request) {
		return request.contentType(MediaType.APPLICATION_JSON).with(servletRequest -> {
			servletRequest.setRemoteAddr(ip);
			return servletRequest;
		});
	}

}
