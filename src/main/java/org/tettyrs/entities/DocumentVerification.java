package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.tettyrs.entities.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_document_verifications", indexes = {
        @Index(name = "idx_verify_document_id", columnList = "document_id"),
        @Index(name = "idx_verify_status", columnList = "verification_status"),
        @Index(name = "idx_verify_created_at", columnList = "created_at")
})
@NoArgsConstructor
public class DocumentVerification extends PanacheEntity {

    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    public Document document;

    @Column(name = "verification_status")
    @Enumerated(EnumType.STRING)
    public VerificationStatus verificationStatus;

    @Column(name = "verification_details", columnDefinition = "TEXT")
    public String verificationDetails;

    @Column(name = "confidence_score")
    public Float confidenceScore;

    @Column(name = "verified_by")
    public String verifiedBy;

    @Column(name = "verified_at")
    public LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    public String notes;
}
