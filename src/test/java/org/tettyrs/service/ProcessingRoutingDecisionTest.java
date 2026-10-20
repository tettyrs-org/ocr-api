package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keputusan routing: KTP dan reimburse lewat ms-ocr, kategori lain diproses lokal. */
class ProcessingRoutingDecisionTest {

    @Test
    void identityAndReimburseUseEngineExtraction() {
        assertTrue(ProcessingService.usesEngineExtraction("identity"));
        assertTrue(ProcessingService.usesEngineExtraction("reimburse"));
    }

    @Test
    void otherAndUnknownCategoriesDoNotUseEngineExtraction() {
        assertFalse(ProcessingService.usesEngineExtraction("other"));
        assertFalse(ProcessingService.usesEngineExtraction(null));
    }
}
