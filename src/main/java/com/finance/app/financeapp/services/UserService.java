package com.finance.app.financeapp.services;

import com.finance.app.financeapp.dto.ChangeEmailRequest;
import com.finance.app.financeapp.dto.ChangePasswordRequest;
import com.finance.app.financeapp.dto.DeleteAccountRequest;
import com.finance.app.financeapp.dto.UserRegisterRequest;
import com.finance.app.financeapp.dto.UserResponse;
import com.finance.app.financeapp.dto.UserUpdateRequest;
import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.resources.exceptions.EmailAlreadyUsedException;
import com.finance.app.financeapp.resources.exceptions.InvalidCredentialsException;
import com.finance.app.financeapp.resources.exceptions.WrongPasswordException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operacoes da conta. Todas, exceto o cadastro, recebem o id que veio do token:
 * nao existe caminho para ler ou alterar a conta de outra pessoa.
 */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordGuard passwordGuard;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordGuard passwordGuard) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordGuard = passwordGuard;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional
	public UserResponse register(UserRegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyUsedException();
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
				request.payDay());
		return UserResponse.from(saveWithUniqueEmail(user));
	}

	@Transactional(readOnly = true)
	public UserResponse find(UUID userId) {
		return UserResponse.from(load(userId));
	}

	@Transactional
	public UserResponse update(UUID userId, UserUpdateRequest request) {
		User user = load(userId);
		user.setName(request.name().trim());
		user.setPayDay(request.payDay());
		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	// noRollbackFor: a tentativa errada de senha precisa ficar registrada.
	@Transactional(noRollbackFor = WrongPasswordException.class)
	public UserResponse changeEmail(UUID userId, ChangeEmailRequest request) {
		User user = load(userId);
		requireCurrentPassword(user, request.currentPassword());
		String email = normalizeEmail(request.newEmail());
		if (!email.equals(user.getEmail())) {
			if (userRepository.existsByEmail(email)) {
				throw new EmailAlreadyUsedException();
			}
			user.setEmail(email);
		}
		return UserResponse.from(saveWithUniqueEmail(user));
	}

	@Transactional(noRollbackFor = WrongPasswordException.class)
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		User user = load(userId);
		requireCurrentPassword(user, request.currentPassword());
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.incrementTokenVersion();
		userRepository.saveAndFlush(user);
	}

	@Transactional(noRollbackFor = WrongPasswordException.class)
	public void delete(UUID userId, DeleteAccountRequest request) {
		User user = load(userId);
		requireCurrentPassword(user, request.currentPassword());
		userRepository.delete(user);
	}

	private User load(UUID userId) {
		return userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
	}

	private void requireCurrentPassword(User user, String rawPassword) {
		if (!passwordGuard.verify(user, rawPassword)) {
			throw new WrongPasswordException();
		}
	}

	// A constraint do banco e a garantia final contra dois cadastros simultaneos com o mesmo e-mail.
	private User saveWithUniqueEmail(User user) {
		try {
			return userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new EmailAlreadyUsedException();
		}
	}

}
