package org.tettyrs.service;

import org.junit.jupiter.api.Test;
import org.tettyrs.entities.enums.DocumentType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessingServiceMappingTest {

    @Test
    void kategoriBensinParkirDanKwitansiDipetakanDariHasilKlasifikasi() {
        assertEquals(DocumentType.REIMBURSEMENT_FUEL, ProcessingService.mapReimburseType("BBM / bensin"));
        assertEquals(DocumentType.PARKING_CARD, ProcessingService.mapReimburseType("Kartu Parkir"));
        assertEquals(DocumentType.RECEIPT, ProcessingService.mapReimburseType("struk belanja"));
    }

    @Test
    void suratTugasDanSpdTermasukReimburseDanDikenali() {
        assertEquals(DocumentType.SURAT_TUGAS, ProcessingService.mapReimburseType("Surat Tugas"));
        assertEquals(DocumentType.SPD, ProcessingService.mapReimburseType("SPD perjalanan dinas"));
    }

    @Test
    void nilaiKosongYangTidakDikenalJatuhKeRECEIPTBukanDitebak() {
        assertEquals(DocumentType.RECEIPT, ProcessingService.mapReimburseType(null));
        assertEquals(DocumentType.RECEIPT, ProcessingService.mapReimburseType("sesuatu yang tidak jelas"));
    }
}
