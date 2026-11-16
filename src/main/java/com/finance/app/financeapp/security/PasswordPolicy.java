package com.finance.app.financeapp.security;

/** Limites de senha usados na validacao de entrada e na conferencia. */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	/** O BCrypt so considera os primeiros 72 bytes. */
	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

}
