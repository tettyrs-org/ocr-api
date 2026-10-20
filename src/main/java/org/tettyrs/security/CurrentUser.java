package org.tettyrs.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.Arrays;

@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    public boolean isAuthenticated() {
        return identity != null && !identity.isAnonymous();
    }

    public String userId() {
        return identity.getPrincipal().getName();
    }

    public boolean hasAnyRole(String... roles) {
        return Arrays.stream(roles).anyMatch(identity.getRoles()::contains);
    }
}
