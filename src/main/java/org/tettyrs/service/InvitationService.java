package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.AppUser;
import org.tettyrs.entities.Invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Undangan pegawai sekali pakai (api-spec.md bagian 16). Token asli hanya dipegang BFF dan pegawai.
 * API menyimpan hash SHA-256 dan tidak pernah bisa membuat ulang token dari hash.
 */
@ApplicationScoped
public class InvitationService {

    /** Penolakan bisnis dengan kode error spesifikasi. */
    public static class Rejected extends RuntimeException {
        public final ErrorCode code;
        public final String field;

        public Rejected(ErrorCode code, String message, String field) {
            super(message);
            this.code = code;
            this.field = field;
        }
    }

    public record Check(String email, OffsetDateTime expiresAt) {
    }

    public record Redeemed(String uid, String email) {
    }

    /** Hash SHA-256 (hex) dari token. Sama dengan yang dihitung BFF saat membuat undangan. */
    public static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    /** Membuat undangan baru untuk akun yang belum lengkap profilnya. Undangan lama dinonaktifkan. */
    @Transactional
    public void replace(String uid, String tokenHash, OffsetDateTime expiresAt) {
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "token_hash tidak valid.", "token_hash");
        }
        AppUser user = AppUser.findById(uid);
        if (user == null) {
            throw new Rejected(ErrorCode.NOT_FOUND, "Pengguna tidak ditemukan.", null);
        }
        if (user.profileCompleted) {
            throw new Rejected(ErrorCode.PROFILE_ALREADY_COMPLETE, "Profil sudah lengkap.", null);
        }
        Invitation.delete("uid = ?1", uid);
        Invitation inv = new Invitation();
        inv.uid = uid;
        inv.tokenHash = tokenHash;
        inv.expiresAt = expiresAt;
        inv.persist();
    }

    /** Memeriksa token tanpa mengubah apa pun. Terpakai, kedaluwarsa, dan salah dibedakan dengan kode yang sama. */
    public Check check(String token) {
        Invitation inv = lookup(token);
        AppUser user = AppUser.findById(inv.uid);
        return new Check(user == null ? null : user.email, inv.expiresAt);
    }

    /**
     * Menandai undangan terpakai dan mengisi profil. Baris pengguna dan undangan dikunci agar dua
     * pengiriman bersamaan tidak lolos keduanya.
     */
    @Transactional
    public Redeemed redeem(String token, ProfileService.Input input, ProfileService profiles) {
        Invitation inv = lookup(token);
        AppUser user = AppUser.findById(inv.uid);
        if (user == null) throw invalid();
        Invitation.getEntityManager().lock(user, LockModeType.PESSIMISTIC_WRITE);
        Invitation locked = Invitation.getEntityManager().find(Invitation.class, inv.id, LockModeType.PESSIMISTIC_WRITE);
        if (locked.usedAt != null || locked.expiresAt.isBefore(OffsetDateTime.now())) throw invalid();
        try {
            profiles.complete(inv.uid, input);
        } catch (ProfileService.Rejected e) {
            throw new Rejected(e.code, e.getMessage(), e.field);
        }
        locked.usedAt = OffsetDateTime.now();
        return new Redeemed(inv.uid, user.email);
    }

    /** Mengembalikan undangan dan profil ke keadaan semula bila pemasangan sandi gagal di Firebase. */
    @Transactional
    public void release(String token) {
        Invitation inv = Invitation.find("tokenHash", hash(token)).firstResult();
        if (inv == null || inv.usedAt == null) throw invalid();
        AppUser user = AppUser.findById(inv.uid);
        if (user != null) {
            user.fullName = null;
            user.nip = null;
            user.profileCompleted = false;
        }
        inv.usedAt = null;
    }

    private Invitation lookup(String token) {
        if (token == null || token.isBlank()) throw invalid();
        Invitation inv = Invitation.find("tokenHash", hash(token)).firstResult();
        if (inv == null || inv.usedAt != null || inv.expiresAt.isBefore(OffsetDateTime.now())) throw invalid();
        return inv;
    }

    private static Rejected invalid() {
        return new Rejected(ErrorCode.INVITATION_INVALID, "Tautan undangan tidak valid atau sudah tidak berlaku.", null);
    }
}
