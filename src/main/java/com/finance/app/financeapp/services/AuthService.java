package com.finance.app.financeapp.services;

import com.finance.app.financeapp.dto.LoginRequest;
import com.finance.app.financeapp.dto.TokenResponse;
import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.resources.exceptions.InvalidCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordGuard passwordGuard;
	private final TokenService tokenService;

	public AuthService(UserRepository userRepository, PasswordGuard passwordGuard, TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordGuard = passwordGuard;
		this.tokenService = tokenService;
	}

	// noRollbackFor: o contador de tentativas erradas precisa ser gravado mesmo quando o login falha.
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public TokenResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(UserService.normalizeEmail(request.email())).orElse(null);
		if (user == null) {
			passwordGuard.simulateVerification(request.password());
			throw new InvalidCredentialsException();
		}
		if (!passwordGuard.verify(user, request.password())) {
			throw new InvalidCredentialsException();
		}
		return tokenService.issue(user);
	}

}
