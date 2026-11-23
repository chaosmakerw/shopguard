package com.codesprint.lesson0831.trade.core;

/** Safe client-facing error. The cause is retained for server-side diagnosis only. */
public class TradeException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public TradeException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public TradeException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
