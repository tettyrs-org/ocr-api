package org.tettyrs.dto;

import org.junit.jupiter.api.Test;
import org.tettyrs.service.FieldCipher;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pembacaan nilai tersimpan: terenkripsi didekripsi, data lama tanpa prefiks dibaca apa adanya. */
class StoredValueTest {

    private static FieldCipher cipher() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new FieldCipher(Base64.getEncoder().encodeToString(k));
    }

    @Test
    void encryptedValueIsDecrypted() {
        FieldCipher c = cipher();

        assertEquals("{\"nominal\":600000}", DocumentView.readStored(c.encrypt("{\"nominal\":600000}"), c));
    }

    @Test
    void legacyPlainValueIsReturnedAsIs() {
        assertEquals("{\"nominal\":600000}", DocumentView.readStored("{\"nominal\":600000}", cipher()));
    }

    @Test
    void nullStaysNull() {
        assertNull(DocumentView.readStored(null, cipher()));
    }

    @Test
    void encryptedValueWithoutKeyIsNotShownAsCiphertext() {
        String stored = cipher().encrypt("rahasia");

        assertNull(DocumentView.readStored(stored, null));
    }
}
