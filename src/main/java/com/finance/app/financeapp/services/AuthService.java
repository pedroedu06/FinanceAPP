package com.finance.app.financeapp.services;

import com.finance.app.financeapp.dto.LoginRequest;
import com.finance.app.financeapp.dto.TokenResponse;
import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.exceptions.InvalidCredentialsException;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.PasswordGuard;
import com.finance.app.financeapp.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private static final Logger audit = LoggerFactory.getLogger("security.audit");

	private final UserRepository userRepository;
	private final PasswordGuard passwordGuard;
	private final TokenService tokenService;

	public AuthService(UserRepository userRepository, PasswordGuard passwordGuard, TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordGuard = passwordGuard;
		this.tokenService = tokenService;
	}

	public TokenResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(Emails.normalize(request.email())).orElse(null);
		if (user == null) {
			passwordGuard.simulateVerification(request.password());
			audit.warn("Login recusado: e-mail nao cadastrado");
			throw new InvalidCredentialsException();
		}
		if (!passwordGuard.verify(user.getId(), request.password())) {
			throw new InvalidCredentialsException();
		}
		audit.info("Login da conta {}", user.getId());
		return tokenService.issue(user);
	}

}
