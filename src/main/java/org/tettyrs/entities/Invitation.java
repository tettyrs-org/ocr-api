package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

// Undangan pegawai (api-spec.md bagian 16). Hanya hash token yang disimpan, bukan tokennya.
@Entity
@Table(name = "ocr_invitations")
public class Invitation extends PanacheEntity {

    @Column(name = "uid", nullable = false)
    public String uid;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    public String tokenHash;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
