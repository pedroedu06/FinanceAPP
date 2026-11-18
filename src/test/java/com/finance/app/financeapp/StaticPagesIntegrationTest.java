package com.finance.app.financeapp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A tela provisoria e publica (os arquivos nao contem dados), mas isso nao pode
 * abrir mais nada: a API e qualquer outro caminho continuam exigindo token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticPagesIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rootServesTheLoginPage() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/index.html", "/dashboard.html", "/css/app.css", "/js/api.js", "/js/auth.js",
			"/js/forms.js", "/js/dashboard.js", "/js/sample-data.js", "/favicon.svg" })
	void pagesAndAssetsArePublic(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isOk());
	}

	@Test
	void pagesCarryARestrictiveContentSecurityPolicyAndNoInlineCode() throws Exception {
		for (String page : new String[] { "/index.html", "/dashboard.html" }) {
			mockMvc.perform(get(page))
				.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("<style"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("style="))))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("onclick"))));
		}
	}

	@Test
	void everythingElseStillRequiresAToken() throws Exception {
		mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/qualquer-outra-pagina.html")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/js/../users/me")).andExpect(status().is4xxClientError());
		// So GET foi liberado nos arquivos estaticos.
		mockMvc.perform(post("/index.html")).andExpect(status().isUnauthorized());
	}

}
