package com.codesprint.lesson0831.platform;

import java.io.Serializable;
import java.security.Principal;

public record PlatformIdentity(long id, String username, String displayName, String role)
        implements Principal, Serializable {
    @Override public String getName() { return Long.toString(id); }
}
