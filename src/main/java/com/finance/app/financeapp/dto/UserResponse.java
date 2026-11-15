package com.finance.app.financeapp.dto;

import com.finance.app.financeapp.entities.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Integer payDay, Instant createdAt, Instant updatedAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPayDay(),
				user.getCreatedAt(), user.getUpdatedAt());
	}

}
