package org.tettyrs.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class S3ServiceTest {

    private S3Service service(S3Client client, String sse) {
        S3Service s = new S3Service();
        s.s3Client = client;
        s.bucketName = "ocr-documents";
        s.serverSideEncryption = java.util.Optional.ofNullable(sse);
        return s;
    }

    private PutObjectRequest captureRequest(S3Client client) {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        return captor.getValue();
    }

    @Test
    void sendsServerSideEncryptionWhenConfigured() {
        S3Client client = mock(S3Client.class);
        service(client, "AES256").uploadFile("documents/x/a.pdf",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "application/pdf");

        PutObjectRequest req = captureRequest(client);
        assertEquals("AES256", req.serverSideEncryptionAsString());
        assertEquals("ocr-documents", req.bucket());
    }

    @Test
    void omitsEncryptionHeaderWhenBlank() {
        // MinIO lokal tanpa KMS: header tidak boleh dikirim, karena unggahan akan ditolak.
        S3Client client = mock(S3Client.class);
        service(client, "  ").uploadFile("documents/x/a.pdf",
                new ByteArrayInputStream(new byte[]{1}), 1L, "application/pdf");

        assertNull(captureRequest(client).serverSideEncryption());
    }

    @Test
    void uploadFailureIsReportedAsRuntimeException() {
        S3Client client = mock(S3Client.class);
        doThrow(S3Exception.builder().message("boom").build())
                .when(client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

        assertThrows(RuntimeException.class, () -> service(client, "AES256")
                .uploadFile("documents/x/a.pdf", new ByteArrayInputStream(new byte[]{1}), 1L, "application/pdf"));
    }
}
