package com.codesprint.lesson0831.agent;

public class ModelCallException extends RuntimeException {
    public ModelCallException(String message) {
        super(message);
    }
    public ModelCallException(String message,Throwable cause){
        super(message,cause);
    }
}
