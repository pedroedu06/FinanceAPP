package com.finance.app.financeapp.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Senha nova: de 8 caracteres ate 72 bytes em UTF-8 (limite do BCrypt).
 */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPasswordValidator.class)
public @interface ValidPassword {

	String message() default "a senha deve ter de 8 a 72 caracteres";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
