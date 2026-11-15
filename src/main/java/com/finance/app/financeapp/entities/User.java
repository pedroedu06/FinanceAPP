package com.finance.app.financeapp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User implements Serializable {
	@Id
	private UUID id = UUID.randomUUID();

	@Version
	private Long version;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 254, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "pay_day", nullable = false)
	private Integer payDay;

	// Sobe a cada troca de senha; tokens emitidos com o valor antigo deixam de valer.
	@Column(name = "token_version", nullable = false)
	private int tokenVersion;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public User() {}

	public User(String name, String email, String passwordHash, Integer payDay) {
		super();
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.payDay = payDay;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Integer getPayDay() {
		return payDay;
	}

	public void setPayDay(Integer payDay) {
		this.payDay = payDay;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public void incrementTokenVersion() {
		this.tokenVersion++;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public void setFailedLoginAttempts(int failedLoginAttempts) {
		this.failedLoginAttempts = failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public void setLockedUntil(Instant lockedUntil) {
		this.lockedUntil = lockedUntil;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this==o) return true;
		if (!(o instanceof User other)) return false;
		return id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

}
