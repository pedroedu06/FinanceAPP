package com.finance.app.financeapp.security;

import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.TokenService;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Confere, a cada requisicao, se o dono do token ainda existe e se o token foi
 * emitido depois da ultima troca de senha. Token de conta apagada ou anterior a
 * troca de senha e recusado com 401.
 */
@Component
public class TokenOwnerValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_OWNER = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"Token nao pertence a uma conta ativa", null);

	private final UserRepository userRepository;

	public TokenOwnerValidator(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		UUID userId;
		try {
			userId = UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return OAuth2TokenValidatorResult.failure(INVALID_OWNER);
		}
		Object version = jwt.getClaim(TokenService.VERSION_CLAIM);
		if (!(version instanceof Number tokenVersion)) {
			return OAuth2TokenValidatorResult.failure(INVALID_OWNER);
		}
		return userRepository.findById(userId)
			.filter(user -> user.getTokenVersion() == tokenVersion.intValue())
			.map(user -> OAuth2TokenValidatorResult.success())
			.orElseGet(() -> OAuth2TokenValidatorResult.failure(INVALID_OWNER));
	}

}
