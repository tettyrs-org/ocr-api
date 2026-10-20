package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enkripsi berkas KTP dan data pribadi tersimpan. Data uji fiktif. */
class FieldCipherBytesTest {

    private static FieldCipher cipher() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new FieldCipher(Base64.getEncoder().encodeToString(k));
    }

    @Test
    void fileRoundTripRestoresOriginalBytes() {
        FieldCipher c = cipher();
        byte[] plain = "isi berkas KTP fiktif".getBytes(StandardCharsets.UTF_8);

        byte[] stored = c.encryptBytes(plain);

        assertFalse(Arrays.equals(plain, stored), "berkas tersimpan tidak boleh sama dengan aslinya");
        assertArrayEquals(plain, c.decryptBytes(stored));
    }

    @Test
    void tamperedFileIsRejected() {
        FieldCipher c = cipher();
        byte[] stored = c.encryptBytes("data".getBytes(StandardCharsets.UTF_8));
        stored[stored.length - 1] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> c.decryptBytes(stored));
    }

    @Test
    void legacyPlainFileWithoutMagicIsReturnedAsIs() {
        byte[] legacy = "JPEG lama".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(legacy, cipher().decryptBytes(legacy));
    }

    @Test
    void encryptedTextIsDetectedByPrefix() {
        String stored = cipher().encrypt("NIK fiktif");

        assertTrue(stored.startsWith(FieldCipher.PREFIX));
    }
}
