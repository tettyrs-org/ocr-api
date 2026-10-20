package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilePurgeServiceTest {

    @Test
    void s3KeyMatchesUploadLayout() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals("documents/11111111-2222-3333-4444-555555555555/nota.jpg",
                FilePurgeService.s3KeyFor(id, "nota.jpg"));
    }

    @Test
    void ageCutoffIsExpiryDaysBeforeNow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 9, 0);

        assertEquals(LocalDateTime.of(2026, 10, 9, 9, 0), FilePurgeService.ageCutoff(now, 7));
    }
}
