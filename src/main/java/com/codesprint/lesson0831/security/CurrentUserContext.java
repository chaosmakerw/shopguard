package com.codesprint.lesson0831.security;

public interface CurrentUserContext {

    Long getUserId();

    boolean isAdmin();
}
