package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.tettyrs.dto.AuditEventResponse;
import org.tettyrs.entities.AuditEvent;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.AuditAction;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class AuditEventService {


    @Transactional
    public void logAuditEvent(Long documentId, AuditAction action, String actor, String details) {
        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }

        AuditEvent event = new AuditEvent();
        event.document = doc;
        event.action = action;
        event.actor = actor;
        event.details = details;
        event.persist();
    }

    public List<AuditEventResponse> getAuditTrail(Long documentId) {
        return ((List<AuditEvent>) (List<?>) AuditEvent.find("document.id", documentId).list())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AuditEventResponse toResponse(AuditEvent event){
        AuditEventResponse response = new AuditEventResponse();
        response.id = event.id;
        response.action = event.action;
        response.details = event.details;
        response.actor = event.actor;
        response.timestamp = event.timestamp;
        return response;
    }
}
