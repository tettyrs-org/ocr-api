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
    EXTRACTION_IN_PROGRESS("EXTRACTION_IN_PROGRESS", 409, "Extraction still processing"),
    CONFLICT("CONFLICT", 409, "Resource state conflict"),

    FILE_PURGED("FILE_PURGED", 410, "File has been deleted"),

    RATE_LIMITED("RATE_LIMITED", 429, "Rate limit exceeded"),

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
