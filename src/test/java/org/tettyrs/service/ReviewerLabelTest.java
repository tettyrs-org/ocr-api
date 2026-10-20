package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewerLabelTest {

    @Test
    void nameWithNipShowsBoth() {
        assertEquals("Budi Santoso (NIP 198503122010011004)",
                DocumentVerifier.reviewerLabel("Budi Santoso", "198503122010011004"));
    }

    @Test
    void nameWithoutNipShowsNameOnly() {
        assertEquals("Budi Santoso", DocumentVerifier.reviewerLabel("  Budi Santoso ", " "));
    }

    @Test
    void missingNameFallsBackToGenericLabelNotUid() {
        assertEquals("Peninjau", DocumentVerifier.reviewerLabel(null, null));
        assertEquals("Peninjau", DocumentVerifier.reviewerLabel("", "198503122010011004"));
    }
}
