package com.finance.app.financeapp.services;

import com.finance.app.financeapp.dto.ChangeEmailRequest;
import com.finance.app.financeapp.dto.ChangePasswordRequest;
import com.finance.app.financeapp.dto.DeleteAccountRequest;
import com.finance.app.financeapp.dto.UserRegisterRequest;
import com.finance.app.financeapp.dto.UserResponse;
import com.finance.app.financeapp.dto.UserUpdateRequest;
import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.exceptions.EmailAlreadyUsedException;
import com.finance.app.financeapp.exceptions.InvalidCredentialsException;
import com.finance.app.financeapp.exceptions.WrongPasswordException;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.PasswordGuard;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Operacoes da conta. Todas, exceto o cadastro, recebem o id que veio do token:
 * nao existe caminho para ler ou alterar a conta de outra pessoa.
 *
 * As acoes sensiveis conferem a senha atual antes de abrir a transacao da
 * alteracao, para nao segurar uma conexao do banco durante o BCrypt.
 */
@Service
public class UserService {

	private static final Logger audit = LoggerFactory.getLogger("security.audit");
	private static final String EMAIL_CONSTRAINT = "uk_users_email";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordGuard passwordGuard;
	private final TransactionTemplate transaction;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordGuard passwordGuard,
			TransactionTemplate transaction) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordGuard = passwordGuard;
		this.transaction = transaction;
	}

	public UserResponse register(UserRegisterRequest request) {
		String email = Emails.normalize(request.email());
		String passwordHash = passwordEncoder.encode(request.password());
		UserResponse created = transaction.execute(status -> {
			if (userRepository.existsByEmail(email)) {
				throw new EmailAlreadyUsedException();
			}
			User user = new User(request.name().trim(), email, passwordHash, request.payDay());
			return UserResponse.from(saveWithUniqueEmail(user));
		});
		audit.info("Conta {} criada", created.id());
		return created;
	}

	public UserResponse find(UUID userId) {
		return UserResponse.from(load(userId));
	}

	public UserResponse update(UUID userId, UserUpdateRequest request) {
		return transaction.execute(status -> {
			User user = load(userId);
			user.setName(request.name().trim());
			user.setPayDay(request.payDay());
			return UserResponse.from(userRepository.saveAndFlush(user));
		});
	}

	/** Trocar o e-mail invalida os tokens ja emitidos; o cliente precisa logar de novo. */
	public UserResponse changeEmail(UUID userId, ChangeEmailRequest request) {
		requireCurrentPassword(userId, request.currentPassword());
		String email = Emails.normalize(request.newEmail());
		UserResponse updated = transaction.execute(status -> {
			User user = load(userId);
			if (email.equals(user.getEmail())) {
				return null;
			}
			if (userRepository.existsByEmail(email)) {
				throw new EmailAlreadyUsedException();
			}
			user.setEmail(email);
			user.incrementTokenVersion();
			return UserResponse.from(saveWithUniqueEmail(user));
		});
		if (updated == null) {
			return find(userId);
		}
		audit.info("E-mail da conta {} alterado", userId);
		return updated;
	}

	/** Trocar a senha invalida os tokens ja emitidos. */
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		requireCurrentPassword(userId, request.currentPassword());
		String passwordHash = passwordEncoder.encode(request.newPassword());
		transaction.executeWithoutResult(status -> {
			User user = load(userId);
			user.setPasswordHash(passwordHash);
			user.incrementTokenVersion();
			userRepository.saveAndFlush(user);
		});
		audit.info("Senha da conta {} alterada", userId);
	}

	public void delete(UUID userId, DeleteAccountRequest request) {
		requireCurrentPassword(userId, request.currentPassword());
		transaction.executeWithoutResult(status -> userRepository.delete(load(userId)));
		audit.info("Conta {} apagada", userId);
	}

	private User load(UUID userId) {
		return userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
	}

	private void requireCurrentPassword(UUID userId, String rawPassword) {
		if (!passwordGuard.verify(userId, rawPassword)) {
			throw new WrongPasswordException();
		}
	}

	// A constraint do banco e a garantia final contra dois cadastros simultaneos com o mesmo
	// e-mail. Qualquer outra violacao de integridade segue adiante como erro.
	private User saveWithUniqueEmail(User user) {
		try {
			return userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			if (mentionsEmailConstraint(ex)) {
				throw new EmailAlreadyUsedException();
			}
			throw ex;
		}
	}

	private static boolean mentionsEmailConstraint(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			String message = cause.getMessage();
			if (message != null && message.toLowerCase(Locale.ROOT).contains(EMAIL_CONSTRAINT)) {
				return true;
			}
		}
		return false;
	}

}
