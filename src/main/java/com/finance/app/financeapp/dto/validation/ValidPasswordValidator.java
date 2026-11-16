package com.finance.app.financeapp.dto.validation;

import com.finance.app.financeapp.security.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) return false;
		return value.codePointCount(0, value.length()) >= PasswordPolicy.MIN_LENGTH
				&& value.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES;
	}

}
