package com.finance.app.financeapp.dto;

import com.finance.app.financeapp.dto.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@ValidPassword String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[]";
	}

}
