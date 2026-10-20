package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldCipherTest {

    private static String newKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void enkripsiLaluDekripsiMengembalikanTeksAsli() {
        FieldCipher cipher = new FieldCipher(newKey());
        String teks = "NIK 3271010101010001 NAMA CONTOH";

        String stored = cipher.encrypt(teks);

        assertTrue(stored.startsWith("enc:v1:"));
        assertFalse(stored.contains("3271010101010001"), "teks polos tidak boleh terlihat di hasil terenkripsi");
        assertEquals(teks, cipher.decrypt(stored));
    }

    @Test
    void setiapEnkripsiMenghasilkanIvBerbeda() {
        FieldCipher cipher = new FieldCipher(newKey());
        assertFalse(cipher.encrypt("sama").equals(cipher.encrypt("sama")));
    }

    @Test
    void kunciSalahDitolak() {
        String stored = new FieldCipher(newKey()).encrypt("rahasia");
        FieldCipher lain = new FieldCipher(newKey());
        assertThrows(IllegalStateException.class, () -> lain.decrypt(stored));
    }

    @Test
    void dataYangDiubahDitolak() {
        FieldCipher cipher = new FieldCipher(newKey());
        String stored = cipher.encrypt("rahasia");
        // Ubah satu karakter di tengah payload base64
        char[] c = stored.toCharArray();
        int mid = c.length / 2;
        c[mid] = c[mid] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(new String(c)));
    }

    @Test
    void tanpaKunciEnkripsiDitolakDanTidakJatuhKeTeksPolos() {
        FieldCipher tanpaKunci = new FieldCipher("");
        assertThrows(IllegalStateException.class, () -> tanpaKunci.encrypt("apa saja"));
    }

    @Test
    void kunciBukan32ByteDitolak() {
        String pendek = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new FieldCipher(pendek).encrypt("x"));
    }

    @Test
    void nilaiTanpaPrefiksDitolak() {
        FieldCipher cipher = new FieldCipher(newKey());
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("teks polos"));
    }
}
