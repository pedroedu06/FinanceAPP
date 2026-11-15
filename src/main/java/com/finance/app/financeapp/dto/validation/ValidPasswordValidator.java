package com.finance.app.financeapp.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_BYTES = 72;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) return false;
		return value.length() >= MIN_LENGTH && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
	}

}
