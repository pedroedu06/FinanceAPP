package com.finance.app.financeapp.resources;

import com.finance.app.financeapp.dto.LoginRequest;
import com.finance.app.financeapp.dto.TokenResponse;
import com.finance.app.financeapp.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth")
public class AuthResource {

	private final AuthService authService;

	public AuthResource(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping(value = "/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request));
	}

}
