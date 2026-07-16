package com.forumx.security.facade;

import com.forumx.security.model.CustomUserDetails;
import org.springframework.security.core.Authentication;

/**
 * Facade interface exposing current security/authentication context.
 * Decouples the application code from direct dependency on static Spring Security Context.
 */
public interface AuthenticationFacade {

    /**
     * Retrieves the current active Authentication token.
     *
     * @return the current Authentication object or null if not authenticated
     */
    Authentication getAuthentication();

    /**
     * Resolves the current user details from the authentication principal.
     *
     * @return the current CustomUserDetails instance or null if not authenticated
     */
    CustomUserDetails getCurrentUserDetails();
}
