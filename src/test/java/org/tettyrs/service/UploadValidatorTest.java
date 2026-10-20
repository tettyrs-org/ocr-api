package org.tettyrs.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.tettyrs.dto.enums.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadValidatorTest {

    private static byte[] withHead(int totalLength, int... head) {
        byte[] out = new byte[totalLength];
        for (int i = 0; i < head.length; i++) out[i] = (byte) head[i];
        return out;
    }

    private static byte[] pdfWithPages(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void deteksiFormatDariMagicBytes() {
        assertEquals(UploadValidator.Kind.JPEG, UploadValidator.detect(withHead(8, 0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(UploadValidator.Kind.PNG, UploadValidator.detect(withHead(8, 0x89, 0x50, 0x4E, 0x47)));
        assertEquals(UploadValidator.Kind.PDF, UploadValidator.detect(withHead(8, 0x25, 0x50, 0x44, 0x46)));
        assertNull(UploadValidator.detect("GIF89a".getBytes()));
    }

    @Test
    void formatTidakDidukungDitolakSebagaiUnsupportedMediaType() {
        var ex = assertThrows(UploadValidator.RejectedException.class,
                () -> UploadValidator.validate("bukan gambar".getBytes()));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.code);
    }

    @Test
    void gambarMelebihi10MbDitolakSebagaiFileTooLarge() {
        byte[] tooBig = withHead((int) UploadValidator.MAX_IMAGE_BYTES + 1, 0xFF, 0xD8, 0xFF);
        var ex = assertThrows(UploadValidator.RejectedException.class, () -> UploadValidator.validate(tooBig));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.code);
    }

    @Test
    void gambarTepat10MbLolos() {
        byte[] exact = withHead((int) UploadValidator.MAX_IMAGE_BYTES, 0xFF, 0xD8, 0xFF);
        assertEquals(UploadValidator.Kind.JPEG, UploadValidator.validate(exact).kind());
    }

    @Test
    void pdf15HalamanLolosDan16HalamanDitolak() throws IOException {
        var ok = UploadValidator.validate(pdfWithPages(15));
        assertEquals(15, ok.pageCount());

        var ex = assertThrows(UploadValidator.RejectedException.class,
                () -> UploadValidator.validate(pdfWithPages(16)));
        assertEquals(ErrorCode.PAGE_LIMIT_EXCEEDED, ex.code);
    }

    @Test
    void pdfRusakDitolakSebagaiUnsupportedMediaType() {
        byte[] broken = withHead(64, 0x25, 0x50, 0x44, 0x46); // header PDF tanpa isi
        var ex = assertThrows(UploadValidator.RejectedException.class, () -> UploadValidator.validate(broken));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.code);
    }
}
