package com.finance.app.financeapp.resources.exceptions;

/**
 * Falha de login. A mensagem e sempre a mesma para nao revelar se o e-mail
 * existe, se a senha esta errada ou se a conta esta bloqueada.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("E-mail ou senha inválidos.");
	}

}
