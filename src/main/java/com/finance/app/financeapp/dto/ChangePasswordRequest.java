package com.finance.app.financeapp.dto;

import com.finance.app.financeapp.dto.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank @Size(max = 128) String currentPassword,
		@ValidPassword String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[]";
	}

}
