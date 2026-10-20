package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Enkripsi data pribadi sensitif di kolom (AES-256-GCM). Dipakai untuk teks OCR identitas diri (KTP)
 * sampai penyimpanan terenkripsi lengkap aktif (PRD bagian 12, OQ-8).
 *
 * Kunci dibaca dari FIELD_ENCRYPTION_KEY (32 byte, base64). Bila kunci tidak ada, enkripsi GAGAL:
 * tidak ada fallback ke teks polos.
 */
@ApplicationScoped
public class FieldCipher {

    public static final String PREFIX = "enc:v1:";
    // Berkas terenkripsi: magic di awal supaya berkas lama (tanpa enkripsi) bisa dibedakan.
    static final byte[] FILE_MAGIC = {'E', 'N', 'C', '1'};
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String keyBase64;

    // Dibaca dari environment FIELD_ENCRYPTION_KEY (MicroProfile memetakan titik ke garis bawah).
    public FieldCipher(@ConfigProperty(name = "field.encryption.key", defaultValue = "") String keyBase64) {
        this.keyBase64 = keyBase64;
    }

    /** Hasil berformat `enc:v1:<base64(iv || ciphertext || tag)>`. */
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Enkripsi gagal", e);
        }
    }

    /** Membuka hasil `encrypt`. Melempar bila kunci salah atau data diubah (GCM memverifikasi tag). */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Nilai tidak terenkripsi dengan format yang dikenal");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (in.length < IV_BYTES + TAG_BITS / 8) throw new IllegalArgumentException("Data terenkripsi rusak");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] pt = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dekripsi gagal: kunci salah atau data berubah", e);
        }
    }

    /** Enkripsi berkas biner (AES-256-GCM). Format: magic "ENC1" + iv + ciphertext+tag. */
    public byte[] encryptBytes(byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[FILE_MAGIC.length + iv.length + ct.length];
            System.arraycopy(FILE_MAGIC, 0, out, 0, FILE_MAGIC.length);
            System.arraycopy(iv, 0, out, FILE_MAGIC.length, iv.length);
            System.arraycopy(ct, 0, out, FILE_MAGIC.length + iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Enkripsi berkas gagal", e);
        }
    }

    /** Membuka berkas terenkripsi. Berkas tanpa magic (sebelum enkripsi diterapkan) dikembalikan apa adanya. */
    public byte[] decryptBytes(byte[] stored) {
        if (!startsWithMagic(stored)) return stored;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            int off = FILE_MAGIC.length;
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, stored, off, IV_BYTES));
            return cipher.doFinal(stored, off + IV_BYTES, stored.length - off - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dekripsi berkas gagal: kunci salah atau data berubah", e);
        }
    }

    /** Nilai teks sudah terenkripsi (berprefiks `enc:v1:`). Null dianggap tidak perlu dienkripsi. */
    public static boolean isEncryptedText(String v) {
        return v != null && v.startsWith(PREFIX);
    }

    /** Berkas sudah dalam format terenkripsi (berawalan magic). */
    public static boolean hasFileMagic(byte[] b) {
        return startsWithMagic(b);
    }

    private static boolean startsWithMagic(byte[] b) {
        if (b == null || b.length < FILE_MAGIC.length) return false;
        for (int i = 0; i < FILE_MAGIC.length; i++) {
            if (b[i] != FILE_MAGIC[i]) return false;
        }
        return true;
    }

    private SecretKey key() {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("FIELD_ENCRYPTION_KEY belum diatur. Penyimpanan data sensitif ditolak.");
        }
        byte[] raw = Base64.getDecoder().decode(keyBase64.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("FIELD_ENCRYPTION_KEY harus 32 byte");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
