package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.AppUser;
import org.tettyrs.entities.Employee;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Profil pegawai (api-spec.md bagian 12): nama dan NIP dicocokkan dengan master `employees`.
 * Pemanggil mendapat {@link Rejected} dengan kode dan field yang salah.
 */
@ApplicationScoped
public class ProfileService {

    private static final Pattern NIP = Pattern.compile("\\d{18}");

    /** Penolakan bisnis: kode error spesifikasi plus field yang bermasalah (boleh null). */
    public static class Rejected extends RuntimeException {
        public final ErrorCode code;
        public final String field;

        public Rejected(ErrorCode code, String message, String field) {
            super(message);
            this.code = code;
            this.field = field;
        }
    }

    public record Input(String fullName, String nip) {
    }

    public static String normalizeName(String s) {
        return s == null ? "" : s.trim().replaceAll("\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AppUser complete(String uid, Input in) {
        AppUser user = AppUser.findById(uid);
        if (user == null) {
            throw new Rejected(ErrorCode.NOT_FOUND, "Profil pengguna tidak ditemukan.", null);
        }
        if (user.profileCompleted) {
            throw new Rejected(ErrorCode.PROFILE_ALREADY_COMPLETE, "Profil sudah lengkap.", null);
        }
        String name = in == null || in.fullName() == null ? "" : in.fullName().trim().replaceAll("\s+", " ");
        if (name.length() < 3 || name.length() > 100) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "Nama lengkap harus 3 sampai 100 karakter.", "full_name");
        }
        String nip = in.nip() == null ? "" : in.nip().trim();
        if (!NIP.matcher(nip).matches()) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "NIP harus 18 digit.", "nip");
        }
        Employee employee = Employee.find("nip", nip).firstResult();
        if (employee == null) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "NIP tidak ditemukan di data kepegawaian.", "nip");
        }
        if (!normalizeName(employee.nama).equals(normalizeName(name))) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "Nama tidak sesuai dengan data kepegawaian untuk NIP ini.", "full_name");
        }
        if (AppUser.count("nip = ?1 and uid <> ?2", nip, uid) > 0) {
            throw new Rejected(ErrorCode.CONFLICT, "NIP ini sudah dipakai akun lain.", "nip");
        }
        user.fullName = employee.nama;
        user.nip = nip;
        user.profileCompleted = true;
        user.persist();
        return user;
    }

    /** Pencarian master untuk admin: nama (sebagian, tanpa huruf besar-kecil) atau NIP awalan. */
    public List<Employee> search(String q) {
        String term = q == null ? "" : q.trim();
        if (term.isEmpty()) {
            return Employee.<Employee>list("ORDER BY nama").stream().limit(200).toList();
        }
        return Employee.<Employee>list("LOWER(nama) LIKE ?1 OR nip LIKE ?2 ORDER BY nama",
                        "%" + term.toLowerCase(Locale.ROOT) + "%", term + "%")
                .stream().limit(200).toList();
    }
}
