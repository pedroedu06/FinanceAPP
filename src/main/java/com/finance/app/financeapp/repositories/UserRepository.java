package com.finance.app.financeapp.repositories;

import com.finance.app.financeapp.entities.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query("select u.passwordHash from User u where u.id = :id")
	Optional<String> findPasswordHashById(@Param("id") UUID id);

	/** Vazio quando a conta nao esta bloqueada (ou nao existe). */
	@Query("select u.lockedUntil from User u where u.id = :id")
	Optional<Instant> findLockedUntilById(@Param("id") UUID id);

	// Os tres UPDATEs abaixo controlam as tentativas de senha. Cada um e atomico no banco e
	// roda em transacao propria (REQUIRES_NEW), para valer mesmo que quem chamou faca rollback.

	/** Libera a conta cujo bloqueio ja venceu. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Modifying
	@Query(value = """
			UPDATE users SET failed_login_attempts = 0, locked_until = NULL
			WHERE id = :id AND locked_until <= :now""", nativeQuery = true)
	int clearExpiredLock(@Param("id") UUID id, @Param("now") Instant now);

	/**
	 * Reserva uma tentativa antes de a senha ser conferida. Devolve 0 quando a conta
	 * esta bloqueada (ou nao existe): nesse caso a senha nem deve ser avaliada. A
	 * tentativa que atinge o limite ja grava o bloqueio.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Modifying
	@Query(value = """
			UPDATE users SET
				locked_until = CASE WHEN failed_login_attempts + 1 >= :maxAttempts
					THEN CAST(:lockUntil AS TIMESTAMP WITH TIME ZONE) ELSE NULL END,
				failed_login_attempts = failed_login_attempts + 1
			WHERE id = :id AND locked_until IS NULL AND failed_login_attempts < :maxAttempts""", nativeQuery = true)
	int claimPasswordAttempt(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
			@Param("lockUntil") Instant lockUntil);

	/** Senha correta: zera o contador. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Modifying
	@Query(value = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = :id",
			nativeQuery = true)
	int resetPasswordAttempts(@Param("id") UUID id);

}
