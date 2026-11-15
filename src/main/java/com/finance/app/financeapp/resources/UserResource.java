package com.finance.app.financeapp.resources;

import com.finance.app.financeapp.dto.ChangeEmailRequest;
import com.finance.app.financeapp.dto.ChangePasswordRequest;
import com.finance.app.financeapp.dto.DeleteAccountRequest;
import com.finance.app.financeapp.dto.UserRegisterRequest;
import com.finance.app.financeapp.dto.UserResponse;
import com.finance.app.financeapp.dto.UserUpdateRequest;
import com.finance.app.financeapp.services.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A conta e sempre a do dono do token ("/me"): nao ha rota por id nem listagem.
 */
@RestController
@RequestMapping(value = "/users")
public class UserResource {

	private final UserService userService;

	public UserResource(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
		UserResponse created = userService.register(request);
		return ResponseEntity.created(URI.create("/users/me")).body(created);
	}

	@GetMapping(value = "/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().body(userService.find(userId(jwt)));
	}

	@PutMapping(value = "/me")
	public ResponseEntity<UserResponse> update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UserUpdateRequest request) {
		return ResponseEntity.ok().body(userService.update(userId(jwt), request));
	}

	@PutMapping(value = "/me/email")
	public ResponseEntity<UserResponse> changeEmail(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ChangeEmailRequest request) {
		return ResponseEntity.ok().body(userService.changeEmail(userId(jwt), request));
	}

	@PutMapping(value = "/me/password")
	public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(userId(jwt), request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping(value = "/me")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DeleteAccountRequest request) {
		userService.delete(userId(jwt), request);
		return ResponseEntity.noContent().build();
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
