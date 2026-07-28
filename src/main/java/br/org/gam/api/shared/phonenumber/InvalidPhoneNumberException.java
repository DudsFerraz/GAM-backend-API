package br.org.gam.api.shared.phonenumber;

public class InvalidPhoneNumberException extends IllegalArgumentException {

    public InvalidPhoneNumberException(String message) {
        super(message);
    }

    public InvalidPhoneNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
