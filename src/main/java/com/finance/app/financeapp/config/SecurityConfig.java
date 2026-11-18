package com.finance.app.financeapp.config;

import com.finance.app.financeapp.security.JwtProperties;
import com.finance.app.financeapp.security.RateLimitProperties;
import com.finance.app.financeapp.security.TokenOwnerValidator;
import com.finance.app.financeapp.security.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless protegida por JWT. So o cadastro, o login e o health check sao
 * publicos; todo o resto exige um token valido de uma conta ativa.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({ JwtProperties.class, RateLimitProperties.class })
public class SecurityConfig {

	/**
	 * Console do H2: existe apenas no perfil dev. Fora dele esta cadeia nao e criada
	 * e o caminho cai na regra geral (exige token).
	 */
	@Bean
	@Order(1)
	@Profile("dev")
	SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/h2-console/**")
			.csrf(csrf -> csrf.disable())
			// O console usa frames da propria origem.
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
			.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		AuthenticationEntryPoint entryPoint = unauthorizedEntryPoint();
		http
			// Sem cookie de sessao nao ha o que um CSRF explorar: o token vai no header Authorization.
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// As paginas so carregam script, estilo e dados da propria origem, e nada inline.
			.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
					"default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")))
			.authorizeHttpRequests(requests -> requests
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				// Tela provisoria (src/main/resources/static): os arquivos nao contem dado nenhum;
				// os dados continuam vindo da API, que exige token.
				.requestMatchers(HttpMethod.GET, "/", "/index.html", "/dashboard.html", "/favicon.svg",
						"/css/**", "/js/**")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/users", "/auth/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> {})
				.authenticationEntryPoint(entryPoint))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, TokenOwnerValidator tokenOwnerValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// Validade, emissor e audiencia primeiro; o dono do token (consulta ao banco) por ultimo.
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(TokenService.ISSUER),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						audience -> audience != null && audience.contains(TokenService.AUDIENCE)),
				tokenOwnerValidator));
		return decoder;
	}

	private static SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	// Mesmo corpo para token ausente, invalido, expirado ou de conta apagada.
	private static AuthenticationEntryPoint unauthorizedEntryPoint() {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("""
					{"type":"about:blank","title":"Unauthorized","status":401,\
					"detail":"Autenticação necessária ou token inválido."}""");
		};
	}

}
