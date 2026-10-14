package com.example.rag.support;

public final class Errors {

    private Errors() {
    }

    /** 取最内层的原因，异常链最里层那句才是有信息量的 */
    public static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
    }
}
