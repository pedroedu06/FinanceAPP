package com.finance.app.financeapp.services;

import java.util.Locale;

final class Emails {

	private Emails() {
	}

	/** Forma unica em que o e-mail e gravado e buscado: sem espacos nas pontas e em minusculas. */
	static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
