package com.finance.app.financeapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.finance.app.financeapp.dto.ChangePasswordRequest;
import com.finance.app.financeapp.dto.LoginRequest;
import com.finance.app.financeapp.dto.UserRegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class InputValidationTest {

	private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
	private static final Validator validator = factory.getValidator();

	@AfterAll
	static void close() {
		factory.close();
	}

	@Test
	void jwtSecretMustHaveAtLeast32Characters() {
		assertThat(validator.validate(new JwtProperties("curto", Duration.ofHours(1)))).isNotEmpty();
		assertThat(validator.validate(new JwtProperties("", Duration.ofHours(1)))).isNotEmpty();
		assertThat(validator.validate(new JwtProperties("x".repeat(32), Duration.ofHours(1)))).isEmpty();
	}

	@Test
	void newPasswordIsLimitedInBytesNotCharacters() {
		assertThat(passwordErrors("1234567")).isNotEmpty();
		assertThat(passwordErrors("12345678")).isEmpty();
		assertThat(passwordErrors("a".repeat(72))).isEmpty();
		assertThat(passwordErrors("a".repeat(73))).isNotEmpty();
		// 18 emojis sao 72 bytes; 19 passam do limite do BCrypt.
		assertThat(passwordErrors("😀".repeat(18))).isEmpty();
		assertThat(passwordErrors("😀".repeat(19))).isNotEmpty();
		assertThat(passwordErrors("        ")).isNotEmpty();
		assertThat(passwordErrors(null)).isNotEmpty();
	}

	@Test
	void loginAndCurrentPasswordFieldsHaveSizeLimits() {
		assertThat(validator.validate(new LoginRequest("a".repeat(255), "senha"))).isNotEmpty();
		assertThat(validator.validate(new LoginRequest("maria@example.com", "x".repeat(129)))).isNotEmpty();
		assertThat(validator.validate(new LoginRequest("maria@example.com", "x".repeat(128)))).isEmpty();
		assertThat(validator.validate(new ChangePasswordRequest("x".repeat(129), "nova-senha-456"))).isNotEmpty();
	}

	private static java.util.Set<?> passwordErrors(String password) {
		return validator.validateProperty(new UserRegisterRequest("Maria", "maria@example.com", password, 5),
				"password");
	}

}
