package org.tettyrs.dto.enums;

public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", 400, "Input validation failed"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", 400, "File type not supported"),
    PAGE_LIMIT_EXCEEDED("PAGE_LIMIT_EXCEEDED", 400, "PDF exceeds 15 pages limit"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", 413, "File size exceeds limit"),

    UNAUTHENTICATED("UNAUTHENTICATED", 401, "Authentication required"),
    FORBIDDEN("FORBIDDEN", 403, "Access denied"),

    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", 404, "Document not found"),
    NOT_FOUND("NOT_FOUND", 404, "Resource not found"),

    ALREADY_VERIFIED("ALREADY_VERIFIED", 409, "Document already verified"),
    PROFILE_ALREADY_COMPLETE("PROFILE_ALREADY_COMPLETE", 409, "Profile already completed"),
    PROFILE_INCOMPLETE("PROFILE_INCOMPLETE", 403, "Complete your profile before uploading"),
    INVITATION_INVALID("INVITATION_INVALID", 410, "Invitation is invalid, used, or expired"),
    EXTRACTION_IN_PROGRESS("EXTRACTION_IN_PROGRESS", 409, "Extraction still processing"),
    EXTRACTION_FAILED_NOT_EDITABLE("EXTRACTION_FAILED_NOT_EDITABLE", 409, "Failed extraction cannot be verified; reprocess first"),
    CONFLICT("CONFLICT", 409, "Resource state conflict"),

    FILE_PURGED("FILE_PURGED", 410, "File has been deleted"),

    RATE_LIMITED("RATE_LIMITED", 429, "Rate limit exceeded"),

    DOC_TYPE_NOT_SUPPORTED("DOC_TYPE_NOT_SUPPORTED", 400, "Document type is not enabled"),
    QUALITY_REJECTED("QUALITY_REJECTED", 422, "Image quality is too low to read"),
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "Internal server error");

    public final String code;
    public final int httpStatus;
    public final String description;

    ErrorCode(String code, int httpStatus, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.description = description;
    }
}
