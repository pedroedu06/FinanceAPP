package com.finance.app.financeapp.exceptions;

/**
 * Usuario autenticado nao confirmou a senha atual em uma acao sensivel.
 */
public class WrongPasswordException extends RuntimeException {

	public WrongPasswordException() {
		super("Senha atual incorreta ou conta temporariamente bloqueada.");
	}

}
