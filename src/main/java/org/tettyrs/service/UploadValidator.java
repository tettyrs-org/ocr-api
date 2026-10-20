package org.tettyrs.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.tettyrs.dto.enums.ErrorCode;

import java.io.IOException;

/**
 * Validasi berkas unggahan di server (api-spec bagian 2). Tipe ditentukan dari magic bytes, bukan ekstensi.
 * Klien juga memeriksa, tetapi itu hanya umpan balik cepat.
 */
public final class UploadValidator {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_PDF_BYTES = 25L * 1024 * 1024;
    public static final int MAX_PDF_PAGES = 15;

    private UploadValidator() {
    }

    public enum Kind {
        JPEG("image/jpeg"),
        PNG("image/png"),
        PDF("application/pdf");

        public final String mimeType;

        Kind(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    /** Hasil validasi yang lolos. `pageCount` 1 untuk gambar. */
    public record Result(Kind kind, int pageCount) {
    }

    /** Penolakan dengan kode error API (ErrorCode). */
    public static class RejectedException extends RuntimeException {
        public final ErrorCode code;

        public RejectedException(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static Kind detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return Kind.PNG;
        }
        if (b.length >= 4 && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46) {
            return Kind.PDF;
        }
        return null;
    }

    public static Result validate(byte[] bytes) {
        Kind kind = detect(bytes);
        if (kind == null) {
            throw new RejectedException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Format berkas tidak didukung. Gunakan JPG, PNG, atau PDF.");
        }
        if (kind == Kind.PDF) {
            if (bytes.length > MAX_PDF_BYTES) {
                throw new RejectedException(ErrorCode.FILE_TOO_LARGE, "Ukuran PDF melebihi 25 MB.");
            }
            int pages = countPdfPages(bytes);
            if (pages > MAX_PDF_PAGES) {
                throw new RejectedException(ErrorCode.PAGE_LIMIT_EXCEEDED,
                        "PDF memiliki lebih dari " + MAX_PDF_PAGES + " halaman.");
            }
            return new Result(kind, pages);
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new RejectedException(ErrorCode.FILE_TOO_LARGE, "Ukuran gambar melebihi 10 MB.");
        }
        return new Result(kind, 1);
    }

    private static int countPdfPages(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new RejectedException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "PDF tidak dapat dibaca.");
        }
    }
}
