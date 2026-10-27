package org.tettyrs.service;

import org.junit.jupiter.api.Test;
import org.tettyrs.entities.enums.DocumentType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCategoryTest {

    @Test
    void jenisDikenaliDipetakanKeKategoriPemrosesan() {
        assertEquals("identity", DocumentService.categoryFor(DocumentType.IDENTITY_CARD));
        assertEquals("reimburse", DocumentService.categoryFor(DocumentType.REIMBURSEMENT_FUEL));
        assertEquals("reimburse", DocumentService.categoryFor(DocumentType.SURAT_TUGAS));
        assertEquals("reimburse", DocumentService.categoryFor(DocumentType.SPD));
        assertEquals("reimburse", DocumentService.categoryFor(DocumentType.RECEIPT));
        assertEquals("reimburse", DocumentService.categoryFor(DocumentType.PARKING_CARD));
    }

    @Test
    void jenisTidakDikenaliJatuhKeLainnyaBukanReimburse() {
        // Sad path: dokumen yang tidak dikenali tidak boleh ditebak ke reimburse (akan dipaksa cocok ke struk).
        assertEquals("other", DocumentService.categoryFor(DocumentType.UNKNOWN));
        assertEquals("other", DocumentService.categoryFor(DocumentType.OTHER));
        assertEquals("other", DocumentService.categoryFor(null));
    }

    @Test
    void unggahanAutoDanKtpDienkripsiSaatDisimpan() {
        assertTrue(DocumentService.encryptsAtRest("auto"));
        assertTrue(DocumentService.encryptsAtRest("identity"));
    }

    @Test
    void reimburseDanLainnyaTidakDienkripsiSaatDisimpan() {
        assertFalse(DocumentService.encryptsAtRest("reimburse"));
        assertFalse(DocumentService.encryptsAtRest("other"));
    }

    @Test
    void autoMemakaiPemrosesanMsOcr() {
        assertTrue(ProcessingService.usesEngineExtraction("auto"));
    }
}
