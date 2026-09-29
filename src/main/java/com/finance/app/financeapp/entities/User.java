package com.finance.app.financeapp.entities;

import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class User implements Serializable {
	@Id
	private UUID id = UUID.randomUUID();

	@Version
	private Long version;

	private String name;
	private String email;
	private String password;
	private Integer payDay;
	private Instant createdAt ;

	public User() {}

	public User(String name, String email, String password, Integer payDay) {
		super();
		this.name = name;
		this.email = email;
		this.password = password;
		this.payDay = payDay;
		this.createdAt = Instant.now();
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Integer getPayDay() {
		return payDay;
	}

	public void setPayDay(Integer payDay) {
		this.payDay = payDay;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this==o) return true;
		if (!(o instanceof User other)) return false;
		return id.equals(other.id);
	}

}
