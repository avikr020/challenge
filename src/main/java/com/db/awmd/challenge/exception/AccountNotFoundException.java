package com.db.awmd.challenge.exception;

public class AccountNotFoundException extends RuntimeException {
    private static final String  ACCOUNT_NOT_EXIST= "Account does not exist for id : %s";
    public AccountNotFoundException(String accountId) {
        super(String.format(ACCOUNT_NOT_EXIST, accountId));
    }
}
