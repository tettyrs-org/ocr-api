package org.tettyrs.service;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.tettyrs.dto.DocumentRequest;
import org.tettyrs.dto.DocumentResponse;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.AuditAction;
import org.tettyrs.entities.enums.DocumentStatus;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class DocumentService extends PanacheEntity {

    @Inject
    AuditEventService auditEventService;
    @Inject
    S3Service s3Service;

    @ConfigProperty(name = "tettyrs.s3.max-file-size-mb", defaultValue = "25")
    Long maxFileSIzeMb;

    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        Document doc = new Document();
        doc.filename = request.filename;
        doc.originalFilename = request.filename;
        doc.fileSize = request.fileSize;
        doc.mimeType = request.mimeType;
        doc.documentType = request.documentType;
        doc.status = DocumentStatus.PENDING;
        doc.createdBy = request.createdBy;
        doc.persist();

        // log audit event
        auditEventService.logAuditEvent(doc.id, AuditAction.UPLOADED, request.createdBy, null);

        return toResponse(doc);
    }

    public DocumentResponse getDocumentById(Long id) {
        Document doc = Document.findById(id);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        return toResponse(doc);
    }

    public List<DocumentResponse> listDocuments() {
        return ((List<Document>) (List<?>) Document.listAll())
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateDocumentStatus(Long id, DocumentStatus status){
        Document document = Document.findById(id);
        document.status = status;
        document.persist();

        // log audit event
        auditEventService.logAuditEvent(id, AuditAction.CHANGED, "system", null);
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document doc = Document.findById(id);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        doc.delete();

        // log audit event
        auditEventService.logAuditEvent(id, AuditAction.DELETED, "system", null);
    }

    @Transactional
    public DocumentResponse uploadFile(Long documentId, InputStream fileStream, Long fileSize, String contentType){
        //validation
        if (fileSize > maxFileSIzeMb * 1024 * 1024) {
            throw new IllegalArgumentException("File too large. Max: "+maxFileSIzeMb+ "MB");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("Content-type not allowed: "+contentType);
        }

        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: "+documentId);
        }
        //s3 generate key
        String s3Key = "documents/" +documentId+ "/" +doc.filename;
        //upload to s3
        String s3Path = s3Service.uploadFile(s3Key, fileStream, fileSize, contentType);
        doc.s3Path = s3Path;
        doc.persist();

        //log audit event
        auditEventService.logAuditEvent(documentId, AuditAction.UPLOADED, doc.createdBy, "File uploaded to s3");
        return  toResponse(doc);
    }

    private DocumentResponse toResponse(Document doc) {
        DocumentResponse response = new DocumentResponse();
        response.id = doc.id;
        response.filename = doc.filename;
        response.fileSize = doc.fileSize;
        response.mimeType = doc.mimeType;
        response.s3Path = doc.s3Path;
        response.status = doc.status;
        response.documentType = doc.documentType;
        response.createdBy = doc.createdBy;
        response.createdAt = doc.createdAt;
        response.updatedAt = doc.updatedAt;
        return response;
    }

    // helper method to validate content type
    private boolean isAllowedContentType(String contentType){
        return contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("application/octet-stream")
                );
    }
}
