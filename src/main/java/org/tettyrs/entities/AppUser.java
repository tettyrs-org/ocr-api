package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

// Profil pengguna. Login dikelola Firebase Authentication; tabel ini menyimpan profil dan role
// sebagai salinan dari custom claim `roles`. Kontrak: documents/tech/auth-firebase.md bagian 8.
@Entity
@Table(name = "ocr_users")
public class AppUser extends PanacheEntityBase {

    @Id
    @Column(name = "uid", nullable = false)
    public String uid;

    @Column(name = "email", nullable = false, unique = true)
    public String email;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles", nullable = false, columnDefinition = "TEXT[]")
    public String[] roles;

    @Column(name = "must_change_password", nullable = false)
    public boolean mustChangePassword;

    // Profil (api-spec.md bagian 12). Diisi pegawai lewat PUT /api/v1/me/profile.
    @Column(name = "full_name")
    public String fullName;

    @Column(name = "nip", length = 18)
    public String nip;

    @Column(name = "profile_completed", nullable = false)
    public boolean profileCompleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
