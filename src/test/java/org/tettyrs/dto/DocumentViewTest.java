package org.tettyrs.dto;

import org.junit.jupiter.api.Test;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentViewTest {

    private static Document doc(ProcessingStatus processing, String category) {
        Document d = new Document();
        d.processingStatus = processing;
        d.category = category;
        d.documentType = DocumentType.UNKNOWN;
        d.status = DocumentStatus.PENDING;
        return d;
    }

    @Test
    void sedangDiprosesMenjadiProcessing() {
        assertEquals("processing", DocumentView.apiStatus(doc(ProcessingStatus.PENDING, "reimburse")));
        assertEquals("processing", DocumentView.apiStatus(doc(ProcessingStatus.PROCESSING, "other")));
    }

    @Test
    void gagalMenjadiExtractionFailed() {
        assertEquals("extraction_failed", DocumentView.apiStatus(doc(ProcessingStatus.FAILED, "reimburse")));
    }

    @Test
    void reimburseSelesaiMenjadiExtractedUntukDitinjau() {
        assertEquals("extracted", DocumentView.apiStatus(doc(ProcessingStatus.COMPLETED, "reimburse")));
    }

    @Test
    void lainnyaDanIdentitasSelesaiLangsungTersimpan() {
        assertEquals("verified", DocumentView.apiStatus(doc(ProcessingStatus.COMPLETED, "other")));
        assertEquals("extracted", DocumentView.apiStatus(doc(ProcessingStatus.COMPLETED, "identity")));
    }

    @Test
    void statusVerifiedDariModelSelaluVerified() {
        Document d = doc(ProcessingStatus.PENDING, "reimburse");
        d.status = DocumentStatus.VERIFIED;
        assertEquals("verified", DocumentView.apiStatus(d));
    }
}
