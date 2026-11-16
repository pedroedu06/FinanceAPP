package com.finance.app.financeapp.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finance.app.financeapp.config.PersistenceConfig;
import com.finance.app.financeapp.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// Replace.NONE: usa o H2 em modo PostgreSQL com as migrations do Flyway, igual a aplicacao.
@DataJpaTest
@ActiveProfiles("test")
@Import(PersistenceConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndFindsByEmail() {
		User saved = userRepository.saveAndFlush(new User("Maria", "repo@example.com", "{bcrypt}hash", 26));

		assertThat(userRepository.findByEmail("repo@example.com")).contains(saved);
		assertThat(userRepository.existsByEmail("repo@example.com")).isTrue();
		assertThat(userRepository.existsByEmail("outra@example.com")).isFalse();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void databaseRejectsDuplicateEmail() {
		userRepository.saveAndFlush(new User("Maria", "repo@example.com", "{bcrypt}hash", 26));

		assertThatThrownBy(() -> userRepository
			.saveAndFlush(new User("Outra Maria", "repo@example.com", "{bcrypt}hash", 5)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsPayDayOutOfRange() {
		assertThatThrownBy(() -> userRepository
			.saveAndFlush(new User("Maria", "repo@example.com", "{bcrypt}hash", 32)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
