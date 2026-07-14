package com.forumx.auth.service;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GoogleTokenVerifierService {

    @Value("${app.google.client-id:}")
    private String googleClientId;

    /**
     * Verifies the Google ID Token signature, expiration, issuer, audience, and email verification state.
     * Extracts and returns the verified claims.
     *
     * @param idTokenString the raw Google ID Token string
     * @return the verified Google claims
     */
    public GoogleClaims verify(String idTokenString) {
        try {
            // Build the token verifier with the configured audience (Google Client ID)
            TokenVerifier verifier = TokenVerifier.newBuilder()
                    .setAudience(googleClientId)
                    .build();

            JsonWebSignature idToken = verifier.verify(idTokenString);
            Map<String, Object> payload = idToken.getPayload();

            // Verify email_verified is true
            Boolean emailVerified = (Boolean) payload.get("email_verified");
            if (emailVerified == null || !emailVerified) {
                throw new IllegalArgumentException("Google email is not verified");
            }

            String sub = payload.get("sub").toString();
            String email = payload.get("email").toString();
            String name = payload.containsKey("name") ? payload.get("name").toString() : null;
            String givenName = payload.containsKey("given_name") ? payload.get("given_name").toString() : null;
            String familyName = payload.containsKey("family_name") ? payload.get("family_name").toString() : null;
            String picture = payload.containsKey("picture") ? payload.get("picture").toString() : null;

            return new GoogleClaims(sub, email, givenName, familyName, name, picture);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Google ID Token: " + e.getMessage(), e);
        }
    }

    public record GoogleClaims(
            String googleSub,
            String email,
            String firstName,
            String lastName,
            String fullName,
            String pictureUrl
    ) {}
}
