package com.codesprint.lesson0831.security;

public record AuthenticatedPrincipal(
        Long userId,
        boolean admin
) {
    public AuthenticatedPrincipal {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "认证用户编号必须大于0"
            );
        }
    }
}