package com.finance.app.financeapp.security;

import com.finance.app.financeapp.dto.TokenResponse;
import com.finance.app.financeapp.entities.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	public static final String ISSUER = "financeapp";
	public static final String AUDIENCE = "financeapp-api";
	public static final String VERSION_CLAIM = "ver";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.audience(List.of(AUDIENCE))
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.expiration()))
			.claim(VERSION_CLAIM, user.getTokenVersion())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", properties.expiration().toSeconds());
	}

}
