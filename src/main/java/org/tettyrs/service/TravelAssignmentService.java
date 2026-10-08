package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.tettyrs.dto.TravelAssignmentRequest;
import org.tettyrs.dto.TravelAssignmentResponse;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.TravelAssignment;
import org.tettyrs.entities.enums.AssignmentStatus;
import org.tettyrs.entities.enums.AuditAction;

import java.util.List;
import java.util.stream.Collectors;


@ApplicationScoped
public class TravelAssignmentService {

    @Inject
    AuditEventService auditEventService;


    @Transactional
    public TravelAssignmentResponse createAssignment(TravelAssignmentRequest request){
        Document document = Document.findById(request.documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: "+ request.documentId);
        }
        TravelAssignment assignment = new TravelAssignment();
        assignment.document = document;
        assignment.assignedTo = request.assignedTo;
        assignment.status = AssignmentStatus.PENDING;
        assignment.notes = request.notes;
        assignment.persist();

        // log audit event
        auditEventService.logAuditEvent(request.documentId, AuditAction.ASSIGNED,
                request.assignedTo,  "{\"assignedTo\": \"" + request.assignedTo + "\"}");

        return toResponse(assignment);
    }
    @Transactional
    public TravelAssignmentResponse updateAssignmentStatus(Long id, AssignmentStatus status){
        TravelAssignment assignment = TravelAssignment.findById(id);
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment not found: "+ id);
        }

        assignment.status = status;
        assignment.persist();

        // log audit event
        auditEventService.logAuditEvent(assignment.document.id, AuditAction.CHANGED,
                "system", "{\"status\": \"" + status + "\"}");

        return toResponse(assignment);
    }

    public List<TravelAssignmentResponse> listAssignments(){
        return ((List<TravelAssignment>) (List<?>) TravelAssignment.listAll())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TravelAssignmentResponse> getAssignmentsByUser(String userId){
        return ((List<TravelAssignment>) (List<?>) TravelAssignment.find("assignedTo", userId).list())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList())
                ;
    }
    private TravelAssignmentResponse toResponse(TravelAssignment assignment){
        TravelAssignmentResponse response = new TravelAssignmentResponse();
        response.id = assignment.id;
        response.documentId = assignment.document.id;
        response.assignedTo = assignment.assignedTo;
        response.status = assignment.status;
        response.notes = assignment.notes;
        response.createdAt = assignment.createdAt;
        response.updatedAt = assignment.updatedAt;
        return response;
    }
}
