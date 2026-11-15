package com.finance.app.financeapp.dto;

import com.finance.app.financeapp.dto.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Email @Size(max = 254) String email,
		@ValidPassword String password,
		@NotNull @Min(1) @Max(31) Integer payDay) {

	// Evita que a senha apareca em logs.
	@Override
	public String toString() {
		return "UserRegisterRequest[name=" + name + ", email=" + email + ", payDay=" + payDay + "]";
	}

}
