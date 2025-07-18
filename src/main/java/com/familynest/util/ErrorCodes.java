package com.familynest.util;

public enum ErrorCodes {
    // User registration errors
    USERNAME_TOO_SHORT("USERNAME_TOO_SHORT"),
    USERNAME_ALREADY_TAKEN("USERNAME_ALREADY_TAKEN"),
    EMAIL_INVALID("EMAIL_INVALID"),
    EMAIL_REQUIRED("EMAIL_REQUIRED"),
    EMAIL_ALREADY_REGISTERED("EMAIL_ALREADY_REGISTERED"),
    PASSWORD_TOO_SHORT("PASSWORD_TOO_SHORT"),
    PASSWORDS_DO_NOT_MATCH("PASSWORDS_DO_NOT_MATCH"),
    PASSWORD_CONFIRMATION_MISMATCH("PASSWORD_CONFIRMATION_MISMATCH"),
    FIRST_NAME_REQUIRED("FIRST_NAME_REQUIRED"),
    LAST_NAME_REQUIRED("LAST_NAME_REQUIRED"),
    
    // Network/system errors
    NETWORK_ERROR("NETWORK_ERROR"),
    TIMEOUT_ERROR("TIMEOUT_ERROR"),
    REGISTRATION_FAILED("REGISTRATION_FAILED");

    private final String code;

    ErrorCodes(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
} 