package com.codesprint.lesson0831.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class FakeCurrentUserContext
        implements CurrentUserContext {

    private final Long userId;
    private final boolean admin;

    public FakeCurrentUserContext() {
        this(1001L, false);
    }

    public FakeCurrentUserContext(Long userId) {
        this(userId, false);
    }

    public FakeCurrentUserContext(
            Long userId,
            boolean admin
    ) {
        this.userId = userId;
        this.admin = admin;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }
}