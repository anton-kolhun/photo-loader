package com.photoloader.exception;


public class GenericError {

    private String message;

    private String error;

    public GenericError(String message, String error) {
        this.message = message;
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
