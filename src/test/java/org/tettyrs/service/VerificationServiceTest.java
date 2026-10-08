package org.tettyrs.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentVerification;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.entities.enums.VerificationStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("VerificationService Tests")
@Transactional
class VerificationServiceTest {

    @Inject
    VerificationService verificationService;

    private Document testDoc;
    private UUID testDocumentId;

    @BeforeEach
    void setup() {
        testDoc = new Document();
        testDoc.filename = "test_verification.pdf";
        testDoc.documentType = DocumentType.PASSPORT;
        testDoc.mimeType = "application/pdf";
        testDoc.fileSize = 1024L;
        testDoc.createdBy = "test@example.com";
        testDoc.processingStatus = ProcessingStatus.COMPLETED;
        testDoc.persist();

        testDocumentId = testDoc.id;
        assertNotNull(testDocumentId);
    }

    @Test
    @DisplayName("Should create verification successfully")
    void testCreateVerification() {
        DocumentVerification verification = verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PASSED,
                "{\"rules_passed\": [\"format_check\"]}",
                0.95f,
                "admin@example.com"
        );

        assertNotNull(verification);
        assertNotNull(verification.id);
        assertEquals(testDocumentId, verification.documentId);
        assertEquals(VerificationStatus.PASSED, verification.verificationStatus);
        assertEquals(0.95f, verification.confidenceScore);
        assertEquals("admin@example.com", verification.verifiedBy);
    }

    @Test
    @DisplayName("Should throw exception for non-existent document")
    void testCreateVerificationNonExistentDoc() {
        assertThrows(
                RuntimeException.class,
                () -> verificationService.createVerification(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        VerificationStatus.PASSED,
                        "{}",
                        0.95f,
                        "admin@example.com"
                )
        );
    }

    @Test
    @DisplayName("Should update verification status")
    void testUpdateVerificationStatus() {
        DocumentVerification v = verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PENDING,
                "{}",
                0.0f,
                "admin@example.com"
        );

        DocumentVerification updated = verificationService.updateVerificationStatus(
                v.id,
                testDocumentId,
                VerificationStatus.PASSED
        );

        assertEquals(VerificationStatus.PASSED, updated.verificationStatus);
    }

    @Test
    @DisplayName("Should get latest verification for document")
    void testGetLatestVerification() throws InterruptedException {
        verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PENDING,
                "{}",
                0.0f,
                "admin@example.com"
        );

        Thread.sleep(100);

        verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PASSED,
                "{}",
                0.95f,
                "admin@example.com"
        );

        DocumentVerification latest = verificationService.getLatestVerification(testDocumentId);
        assertNotNull(latest);
        assertEquals(VerificationStatus.PASSED, latest.verificationStatus);
    }

    @Test
    @DisplayName("Should get verifications with status filter")
    void testGetVerificationsWithFilter() {
        verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PASSED,
                "{}",
                0.95f,
                "admin@example.com"
        );

        verificationService.createVerification(
                testDocumentId,
                VerificationStatus.FAILED,
                "{}",
                0.2f,
                "admin@example.com"
        );

        java.util.List<DocumentVerification> passed =
                verificationService.getVerificationsForDocument(testDocumentId, VerificationStatus.PASSED);

        assertEquals(1, passed.size());
        assertEquals(VerificationStatus.PASSED, passed.get(0).verificationStatus);
    }

    @Test
    @DisplayName("Should add notes to verification")
    void testAddNotes() {
        DocumentVerification v = verificationService.createVerification(
                testDocumentId,
                VerificationStatus.PASSED,
                "{}",
                0.95f,
                "admin@example.com"
        );

        verificationService.addNotes(v.id, testDocumentId, "Document looks authentic");

        DocumentVerification updated = DocumentVerification.findById(v.id);
        assertEquals("Document looks authentic", updated.notes);
    }

    @Test
    @DisplayName("Should create batch verifications")
    void testCreateBatchVerifications() {
        DocumentVerification v1 = new DocumentVerification();
        v1.documentId = testDocumentId;
        v1.verificationStatus = VerificationStatus.PENDING;
        v1.verificationDetails = "{}";
        v1.confidenceScore = 0.0f;
        v1.verifiedBy = "admin@example.com";

        DocumentVerification v2 = new DocumentVerification();
        v2.documentId = testDocumentId;
        v2.verificationStatus = VerificationStatus.PENDING;
        v2.verificationDetails = "{}";
        v2.confidenceScore = 0.0f;
        v2.verifiedBy = "admin@example.com";

        verificationService.createBatchVerifications(
                testDocumentId,
                java.util.Arrays.asList(v1, v2)
        );

        java.util.List<DocumentVerification> verifications =
                verificationService.getVerificationsForDocument(testDocumentId, null);

        assertEquals(2, verifications.size());
    }
}
