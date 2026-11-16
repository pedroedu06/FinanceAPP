package com.finance.app.financeapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenOwnerValidatorTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final TokenOwnerValidator validator = new TokenOwnerValidator(userRepository);

	private User user;

	@BeforeEach
	void setUp() {
		user = new User("Maria", "maria@example.com", "{bcrypt}hash", 26);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
	}

	@Test
	void acceptsTokenOfExistingAccountWithCurrentVersion() {
		assertThat(validator.validate(jwt(user.getId().toString(), 0L)).hasErrors()).isFalse();
	}

	@Test
	void rejectsTokenIssuedBeforeCredentialChange() {
		user.incrementTokenVersion();

		assertThat(validator.validate(jwt(user.getId().toString(), 0L)).hasErrors()).isTrue();
	}

	@Test
	void rejectsTokenOfDeletedAccount() {
		assertThat(validator.validate(jwt(UUID.randomUUID().toString(), 0L)).hasErrors()).isTrue();
	}

	@Test
	void rejectsSubjectThatIsNotAnId() {
		assertThat(validator.validate(jwt("nao-e-uuid", 0L)).hasErrors()).isTrue();
		assertThat(validator.validate(jwt(null, 0L)).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingOrNonNumericVersion() {
		assertThat(validator.validate(jwt(user.getId().toString(), null)).hasErrors()).isTrue();
		assertThat(validator.validate(jwt(user.getId().toString(), "0")).hasErrors()).isTrue();
	}

	private static Jwt jwt(String subject, Object version) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.claim("iss", TokenService.ISSUER);
		if (subject != null) {
			builder.subject(subject);
		}
		if (version != null) {
			builder.claim(TokenService.VERSION_CLAIM, version);
		}
		return builder.build();
	}

}
