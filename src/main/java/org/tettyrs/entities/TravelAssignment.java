package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.tettyrs.entities.enums.AssignmentStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_travel_assignments", indexes = {
        @Index(name = "idx_ocr_document_id", columnList = "document_id"),
        @Index(name = "idx_ocr_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_ocr_status", columnList = "status"),
        @Index(name = "idx_ocr_created_at", columnList = "created_at")
})

@NoArgsConstructor
public class TravelAssignment extends PanacheEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    public Document document;

    @Column(nullable = false, name = "assigned_to")
    public String assignedTo; // userId

    @Column(nullable = false, name = "status")
    @Enumerated(EnumType.STRING)
    public AssignmentStatus status = AssignmentStatus.PENDING;

    @Column(columnDefinition = "TEXT", name = "notes")
    public String notes; // optional

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    public LocalDateTime updatedAt;

}
