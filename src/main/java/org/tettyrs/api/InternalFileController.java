package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.service.InternalAuth;
import org.tettyrs.service.S3Service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Berkas untuk ms-ocr (api-spec bagian 8). ms-ocr tidak punya kredensial storage: berkas diambil lewat API,
 * yang tetap menjadi pemilik penyimpanan. Wajib X-Internal-Token.
 */
@Path("/internal/documents")
public class InternalFileController {

    @Inject
    InternalAuth internalAuth;

    @Inject
    S3Service s3Service;

    @Inject
    org.tettyrs.service.FieldCipher fieldCipher;

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.WILDCARD)
    public Response getFile(@PathParam("id") UUID documentId, @HeaderParam(InternalAuth.HEADER) String token) {
        if (!internalAuth.isValid(token)) {
            return error(ErrorCode.UNAUTHENTICATED, "Token internal tidak valid");
        }
        Document doc = Document.findById(documentId);
        if (doc == null || doc.s3Path == null) {
            return error(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
        }
        InputStream stream = s3Service.downloadFile(s3Service.extractKeyFromPath(doc.s3Path));
        if ("identity".equals(doc.category)) {
            // KTP terenkripsi di aplikasi. ms-ocr menerima berkas plain lewat jalur internal ini.
            stream = new java.io.ByteArrayInputStream(
                    fieldCipher.decryptBytes(org.tettyrs.service.DocumentService.readAll(stream)));
        }
        return Response.ok(stream, doc.mimeType).build();
    }

    private static Response error(ErrorCode code, String message) {
        return Response.status(code.httpStatus)
                .entity(ApiResponse.error(code.code, message))
                .build();
    }
}
