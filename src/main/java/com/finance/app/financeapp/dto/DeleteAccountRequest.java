package com.finance.app.financeapp.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank String currentPassword) {

	@Override
	public String toString() {
		return "DeleteAccountRequest[]";
	}

}
