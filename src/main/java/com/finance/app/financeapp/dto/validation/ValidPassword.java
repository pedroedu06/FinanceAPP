package com.finance.app.financeapp.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Senha nova: no minimo 8 caracteres e no maximo 72 bytes em UTF-8 (limite do BCrypt).
 */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPasswordValidator.class)
public @interface ValidPassword {

	String message() default "a senha deve ter no mínimo 8 caracteres e no máximo 72 bytes (acentos e emojis ocupam mais de um)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
