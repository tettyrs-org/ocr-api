package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autentikasi antar-service untuk endpoint /internal/* (api-spec bagian 8).
 * Token dibaca dari INTERNAL_TOKEN. Bila token belum diatur, semua permintaan ditolak.
 */
@ApplicationScoped
public class InternalAuth {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    public InternalAuth(@ConfigProperty(name = "internal.token", defaultValue = "") String token) {
        this.expected = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    /** Perbandingan dalam waktu konstan. Null atau kosong selalu ditolak. */
    public boolean isValid(String presented) {
        if (expected.length == 0 || presented == null) return false;
        return MessageDigest.isEqual(expected, presented.trim().getBytes(StandardCharsets.UTF_8));
    }
}
