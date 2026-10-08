package org.tettyrs.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentVerification;
import org.tettyrs.entities.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class VerificationService {

    @Inject
    DocumentService documentService;

    @Transactional
    public DocumentVerification createVerification(
            Long documentId,
            VerificationStatus status,
            String verificationDetails,
            Float confidenceScore,
            String verifiedBy) {

        try {
            Document doc = Document.findById(documentId);
            if (doc == null) {
                throw new IllegalArgumentException("Document not found: " + documentId);
            }

            DocumentVerification verification = new DocumentVerification();
            verification.documentId = documentId;
            verification.verificationStatus = status;
            verification.verificationDetails = verificationDetails;
            verification.confidenceScore = confidenceScore;
            verification.verifiedBy = verifiedBy;
            verification.verifiedAt = LocalDateTime.now();

            verification.persist();
            Log.info("Verification created for document " + documentId + " with status " + status);

            return verification;
        } catch (Exception e) {
            Log.error("Failed to create verification for document " + documentId, e);
            throw new RuntimeException("Failed to create verification: " + e.getMessage());
        }
    }

    @Transactional
    public DocumentVerification updateVerificationStatus(
            Long verificationId,
            Long documentId,
            VerificationStatus newStatus) {

        try {
            DocumentVerification verification = DocumentVerification.find(
                    "id = ?1 AND documentId = ?2",
                    verificationId, documentId
            ).firstResult();

            if (verification == null) {
                throw new IllegalArgumentException("Verification not found: " + verificationId);
            }

            String oldStatus = verification.verificationStatus.toString();
            verification.verificationStatus = newStatus;

            Log.info("Verification " + verificationId + " status changed from " + oldStatus + " to " + newStatus);

            return verification;
        } catch (Exception e) {
            Log.error("Failed to update verification status", e);
            throw new RuntimeException("Failed to update verification: " + e.getMessage());
        }
    }

    public DocumentVerification getVerificationById(Long verificationId) {
        DocumentVerification verification = DocumentVerification.findById(verificationId);
        if (verification == null) {
            throw new IllegalArgumentException("Verification not found: " + verificationId);
        }
        return verification;
    }

    public List<DocumentVerification> getVerificationsForDocument(
            Long documentId,
            VerificationStatus statusFilter) {

        if (statusFilter != null) {
            return DocumentVerification.find(
                    "documentId = ?1 AND verificationStatus = ?2 ORDER BY createdAt DESC",
                    documentId, statusFilter
            ).list();
        } else {
            return DocumentVerification.find(
                    "documentId = ?1 ORDER BY createdAt DESC",
                    documentId
            ).list();
        }
    }

    public DocumentVerification getLatestVerification(Long documentId) {
        return DocumentVerification.find(
                "documentId = ?1 ORDER BY id DESC",
                documentId
        ).firstResult();
    }

    @Transactional
    public DocumentVerification addNotes(Long verificationId, Long documentId, String notes) {
        try {
            DocumentVerification verification = DocumentVerification.find(
                    "id = ?1 AND documentId = ?2",
                    verificationId, documentId
            ).firstResult();

            if (verification == null) {
                throw new IllegalArgumentException("Verification not found");
            }

            verification.notes = notes;
            return verification;
        } catch (Exception e) {
            Log.error("Failed to add notes to verification", e);
            throw new RuntimeException("Failed to add notes: " + e.getMessage());
        }
    }


    @Transactional
    public void createBatchVerifications(
            Long documentId,
            List<DocumentVerification> verifications) {

        try {
            for (DocumentVerification verification : verifications) {
                verification.documentId = documentId;
                verification.persist();
            }
            Log.info("Batch created " + verifications.size() + " verifications for document " + documentId);
        } catch (Exception e) {
            Log.error("Failed to batch create verifications", e);
            throw new RuntimeException("Batch verification failed: " + e.getMessage());
        }
    }

}
