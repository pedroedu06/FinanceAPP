package com.finance.app.financeapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finance.app.financeapp.dto.ChangeEmailRequest;
import com.finance.app.financeapp.dto.ChangePasswordRequest;
import com.finance.app.financeapp.dto.DeleteAccountRequest;
import com.finance.app.financeapp.dto.UserRegisterRequest;
import com.finance.app.financeapp.dto.UserResponse;
import com.finance.app.financeapp.entities.User;
import com.finance.app.financeapp.exceptions.EmailAlreadyUsedException;
import com.finance.app.financeapp.exceptions.WrongPasswordException;
import com.finance.app.financeapp.repositories.UserRepository;
import com.finance.app.financeapp.security.PasswordGuard;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserServiceTest {

	private static final String PASSWORD = "senha-correta-1";

	private final UserRepository userRepository = mock(UserRepository.class);
	// Custo 4 so para o teste rodar rapido.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final PasswordGuard passwordGuard = mock(PasswordGuard.class);
	private final UserService service = new UserService(userRepository, passwordEncoder, passwordGuard,
			new TransactionTemplate(mock(PlatformTransactionManager.class)));

	private User existing;

	@BeforeEach
	void setUp() {
		existing = new User("Maria", "maria@example.com", passwordEncoder.encode(PASSWORD), 26);
		when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(passwordGuard.verify(existing.getId(), PASSWORD)).thenReturn(true);
	}

	@Test
	void registerStoresHashAndNormalizedEmail() {
		UserResponse response = service
			.register(new UserRegisterRequest("  João  ", "  Joao@Example.COM ", "minha-senha-123", 10));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getEmail()).isEqualTo("joao@example.com");
		assertThat(saved.getName()).isEqualTo("João");
		assertThat(saved.getPasswordHash()).isNotEqualTo("minha-senha-123").doesNotContain("minha-senha-123");
		assertThat(passwordEncoder.matches("minha-senha-123", saved.getPasswordHash())).isTrue();
		assertThat(response.email()).isEqualTo("joao@example.com");
	}

	@Test
	void registerRejectsEmailAlreadyUsedIgnoringCase() {
		when(userRepository.existsByEmail("maria@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service
			.register(new UserRegisterRequest("Outra", "MARIA@example.com", "minha-senha-123", 10)))
			.isInstanceOf(EmailAlreadyUsedException.class);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerTranslatesOnlyTheEmailConstraintIntoEmailAlreadyUsed() {
		UserRegisterRequest request = new UserRegisterRequest("João", "joao@example.com", "minha-senha-123", 10);

		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(new DataIntegrityViolationException("violates unique constraint \"UK_USERS_EMAIL\""));
		assertThatThrownBy(() -> service.register(request)).isInstanceOf(EmailAlreadyUsedException.class);

		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(new DataIntegrityViolationException("violates check constraint \"ck_users_pay_day\""));
		assertThatThrownBy(() -> service.register(request)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void changePasswordReplacesHashAndInvalidatesOldTokens() {
		int versionBefore = existing.getTokenVersion();

		service.changePassword(existing.getId(), new ChangePasswordRequest(PASSWORD, "nova-senha-456"));

		assertThat(passwordEncoder.matches("nova-senha-456", existing.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches(PASSWORD, existing.getPasswordHash())).isFalse();
		assertThat(existing.getTokenVersion()).isEqualTo(versionBefore + 1);
	}

	@Test
	void changePasswordWithWrongCurrentPasswordChangesNothing() {
		String hashBefore = existing.getPasswordHash();

		assertThatThrownBy(() -> service.changePassword(existing.getId(),
				new ChangePasswordRequest("errada", "nova-senha-456")))
			.isInstanceOf(WrongPasswordException.class);

		assertThat(existing.getPasswordHash()).isEqualTo(hashBefore);
		assertThat(existing.getTokenVersion()).isZero();
		verify(passwordGuard).verify(existing.getId(), "errada");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void changeEmailRequiresCurrentPassword() {
		assertThatThrownBy(() -> service.changeEmail(existing.getId(),
				new ChangeEmailRequest("nova@example.com", "errada")))
			.isInstanceOf(WrongPasswordException.class);

		assertThat(existing.getEmail()).isEqualTo("maria@example.com");
	}

	@Test
	void changeEmailNormalizesAndInvalidatesOldTokens() {
		UserResponse response = service.changeEmail(existing.getId(),
				new ChangeEmailRequest(" Nova@Example.com ", PASSWORD));

		assertThat(response.email()).isEqualTo("nova@example.com");
		assertThat(existing.getTokenVersion()).isEqualTo(1);
	}

	@Test
	void changeEmailToTheSameEmailKeepsTokensValid() {
		service.changeEmail(existing.getId(), new ChangeEmailRequest("MARIA@example.com", PASSWORD));

		assertThat(existing.getTokenVersion()).isZero();
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void changeEmailRejectsEmailOfAnotherAccount() {
		when(userRepository.existsByEmail("ocupado@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.changeEmail(existing.getId(),
				new ChangeEmailRequest("Ocupado@example.com", PASSWORD)))
			.isInstanceOf(EmailAlreadyUsedException.class);

		assertThat(existing.getEmail()).isEqualTo("maria@example.com");
	}

	@Test
	void deleteRequiresCurrentPassword() {
		assertThatThrownBy(() -> service.delete(existing.getId(), new DeleteAccountRequest("errada")))
			.isInstanceOf(WrongPasswordException.class);
		verify(userRepository, never()).delete(any());

		service.delete(existing.getId(), new DeleteAccountRequest(PASSWORD));
		verify(userRepository).delete(existing);
	}

}
