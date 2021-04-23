package ru.citeck.ecos.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String GUEST = "ROLE_GUEST";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    public static final String SYSTEM_USER = "system";

    private AuthoritiesConstants() {
    }
}
