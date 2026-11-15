package com.finance.app.financeapp.resources.exceptions;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException() {
		super("E-mail já cadastrado.");
	}

}
