package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.tettyrs.dto.enums.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    public String code;
    public String message;
    public String field;
    public Object details;


    public ApiError() {
    }

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ApiError(String code, String message, String field) {
        this.code = code;
        this.message = message;
        this.field = field;
    }

    public ApiError(String code, String message, String field, Object details) {
        this.code = code;
        this.message = message;
        this.field = field;
        this.details = details;
    }
    
    public static ApiError of(ErrorCode errorCode, String message){
        return new ApiError(errorCode.code, message);
    }

    public static ApiError of(ErrorCode errorCode, String message, String field){
        return new ApiError(errorCode.code, message, field);
    }

    public static ApiError validation(String field, String message){
        return new ApiError("VALIDATION_ERROR", message, field);
    }
}
