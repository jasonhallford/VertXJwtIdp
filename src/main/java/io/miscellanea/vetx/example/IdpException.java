package io.miscellanea.vetx.example;

public class IdpException extends RuntimeException {
    // Constructors
    public IdpException(String message){
        super(message);
    }

    public IdpException(String message, Throwable cause){
        super(message,cause);
    }
}