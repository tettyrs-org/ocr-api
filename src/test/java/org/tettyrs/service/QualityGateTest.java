package org.tettyrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void blockedResponseIsMappedWithIssues() throws Exception {
        String json = "{\"blocked\": true, \"issues\": [{\"code\": \"buram\", \"level\": \"tolak\", \"message\": \"Gambar buram.\"}]}";

        QualityGate.Decision d = QualityGate.decide(MAPPER.readTree(json));

        assertTrue(d.blocked());
        assertEquals("buram", d.issues().get(0).code());
    }

    @Test
    void warningOnlyResponseIsNotBlocked() throws Exception {
        String json = "{\"blocked\": false, \"issues\": [{\"code\": \"agak_buram\", \"level\": \"peringatan\", \"message\": \"Sedikit buram.\"}]}";

        QualityGate.Decision d = QualityGate.decide(MAPPER.readTree(json));

        assertFalse(d.blocked());
        assertEquals("peringatan", d.issues().get(0).level());
    }

    @Test
    void unavailableEngineFailsOpenWithWarning() {
        // Sad path: engine tidak bisa dihubungi. Unggahan diterima, dengan peringatan, tidak diblokir.
        QualityGate.Decision d = QualityGate.unavailable();

        assertFalse(d.blocked());
        assertEquals("pemeriksaan_tidak_tersedia", d.issues().get(0).code());
    }
}
