package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aturan backfill: hanya data yang belum terenkripsi yang diubah, sehingga aman dijalankan ulang. */
class EncryptionBackfillRulesTest {

    private static FieldCipher cipher() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new FieldCipher(Base64.getEncoder().encodeToString(k));
    }

    @Test
    void plainTextNeedsEncryption() {
        assertTrue(EncryptionBackfillService.needsText("{\"nominal\":600000}"));
    }

    @Test
    void alreadyEncryptedTextIsSkipped() {
        assertFalse(EncryptionBackfillService.needsText(cipher().encrypt("{\"nominal\":600000}")));
    }

    @Test
    void emptyValueIsSkipped() {
        assertFalse(EncryptionBackfillService.needsText(null));
    }

    @Test
    void plainFileNeedsEncryptionButEncryptedFileDoesNot() {
        byte[] jpeg = "\u00ff\u00d8\u00ff isi berkas fiktif".getBytes(StandardCharsets.ISO_8859_1);
        FieldCipher c = cipher();

        assertTrue(EncryptionBackfillService.needsFile(jpeg));
        assertFalse(EncryptionBackfillService.needsFile(c.encryptBytes(jpeg)));
    }
}
